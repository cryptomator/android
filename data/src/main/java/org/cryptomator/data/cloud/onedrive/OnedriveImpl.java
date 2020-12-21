package org.cryptomator.data.cloud.onedrive;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.microsoft.graph.concurrency.ChunkedUploadProvider;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.DriveItemUploadableProperties;
import com.microsoft.graph.models.extensions.Folder;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.models.extensions.ItemReference;
import com.microsoft.graph.models.extensions.UploadSession;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.extensions.IDriveItemCollectionPage;
import com.microsoft.graph.requests.extensions.IDriveItemContentStreamRequest;
import com.microsoft.graph.requests.extensions.IDriveRequestBuilder;
import com.tomclaw.cache.DiskLruCache;

import org.cryptomator.data.cloud.onedrive.graph.ClientException;
import org.cryptomator.data.cloud.onedrive.graph.ICallback;
import org.cryptomator.data.cloud.onedrive.graph.IProgressCallback;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.ExceptionUtil;
import org.cryptomator.util.Optional;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.concurrent.CompletableFuture;
import org.cryptomator.util.file.LruFileCacheUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import timber.log.Timber;

import static java.util.Collections.singletonList;
import static org.cryptomator.data.util.CopyStream.copyStreamToStream;
import static org.cryptomator.data.util.CopyStream.toByteArray;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.ONEDRIVE;
import static org.cryptomator.util.file.LruFileCacheUtil.retrieveFromLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.storeToLruCache;

class OnedriveImpl {

	private static final long CHUNKED_UPLOAD_MAX_SIZE = 4L << 20;
	private static final int CHUNKED_UPLOAD_CHUNK_SIZE = 327680 * 32;
	private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

	private final OnedriveCloud cloud;
	private final Context context;
	private static final String REPLACE_MODE = "replace";
	private static final String NON_REPLACING_MODE = "rename";
	private final OnedriveIdCache nodeInfoCache;
	private final OnedriveClientFactory clientFactory;
	private final SharedPreferencesHandler sharedPreferencesHandler;

	private DiskLruCache diskLruCache;

	OnedriveImpl(OnedriveCloud cloud, Context context, OnedriveIdCache nodeInfoCache) {
		if (cloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}
		this.cloud = cloud;
		this.context = context;
		this.nodeInfoCache = nodeInfoCache;
		this.clientFactory = OnedriveClientFactory.instance(context, cloud.accessToken());

		sharedPreferencesHandler = new SharedPreferencesHandler(context);
	}

	private IGraphServiceClient client() {
		return clientFactory.client();
	}

	private IDriveRequestBuilder drive(String driveId) {
		return driveId == null ? client().me().drive() : client().drives(driveId);
	}

	public OnedriveFolder root() {
		return new RootOnedriveFolder(cloud);
	}

	public OnedriveFolder resolve(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		OnedriveFolder folder = root();
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public OnedriveFile file(OnedriveFolder parent, String name) {
		return file(parent, name, Optional.empty());
	}

	public OnedriveFile file(OnedriveFolder parent, String name, Optional<Long> size) {
		return OnedriveCloudNodeFactory.file(parent, name, size);
	}

	public OnedriveFolder folder(OnedriveFolder parent, String name) {
		return OnedriveCloudNodeFactory.folder(parent, name);
	}

	private DriveItem childByName(String parentId, String parentDriveId, String name) {
		try {
			return drive(parentDriveId) //
					.items(parentId) //
					.itemWithPath(Uri.encode(name)) //
					.buildRequest() //
					.get();
		} catch (GraphServiceException e) {
			if (isNotFoundError(e)) {
				return null;
			} else {
				throw e;
			}
		}
	}

	private boolean isNotFoundError(GraphServiceException error) {
		try {
			Field responseCodeField = GraphServiceException.class.getDeclaredField("responseCode");
			responseCodeField.setAccessible(true);
			Integer responseCode = (Integer) responseCodeField.get(error);
			return responseCode == 404;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean exists(OnedriveNode node) {
		try {
			OnedriveIdCache.NodeInfo parentNodeInfo = nodeInfo(node.getParent());
			if (parentNodeInfo == null) {
				removeNodeInfo(node);
				return false;
			}
			DriveItem item = childByName(parentNodeInfo.getId(), parentNodeInfo.getDriveId(), node.getName());
			if (item == null) {
				removeNodeInfo(node);
				return false;
			}
			cacheNodeInfo(node, item);
			return true;
		} catch (ClientException e) {
			if (ExceptionUtil.contains(e, SocketTimeoutException.class)) {
				throw e;
			}
			return false;
		}
	}

	public List<CloudNode> list(OnedriveFolder folder) throws BackendException {
		List<CloudNode> result = new ArrayList<>();
		OnedriveIdCache.NodeInfo nodeInfo = requireNodeInfo(folder);
		IDriveItemCollectionPage page = drive(nodeInfo.getDriveId()) //
				.items(nodeInfo.getId()) //
				.children() //
				.buildRequest() //
				.get();
		do {
			removeChildNodeInfo(folder);
			for (DriveItem item : page.getCurrentPage()) {
				result.add(cacheNodeInfo(OnedriveCloudNodeFactory.from(folder, item), item));
			}
			if (page.getNextPage() != null) {
				page = page.getNextPage() //
						.buildRequest() //
						.get();
			} else {
				page = null;
			}
		} while (page != null);
		return result;
	}

	public OnedriveFolder create(OnedriveFolder folder) throws NoSuchCloudFileException {
		OnedriveFolder parent = folder.getParent();
		if (nodeInfo(parent) == null) {
			parent = create(folder.getParent());
		}

		final DriveItem folderToCreate = new DriveItem();
		folderToCreate.name = folder.getName();
		folderToCreate.folder = new Folder();

		OnedriveIdCache.NodeInfo parentNodeInfo = requireNodeInfo(parent);
		DriveItem createdFolder = drive(parentNodeInfo.getDriveId()) //
				.items(parentNodeInfo.getId()).children() //
				.buildRequest() //
				.post(folderToCreate);
		return cacheNodeInfo(OnedriveCloudNodeFactory.folder(parent, createdFolder), createdFolder);
	}

	public OnedriveNode move(OnedriveNode source, OnedriveNode target) throws NoSuchCloudFileException, CloudNodeAlreadyExistsException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		final DriveItem targetItem = new DriveItem();
		targetItem.name = target.getName();
		ItemReference targetParentReference = new ItemReference();
		OnedriveIdCache.NodeInfo targetNodeInfo = nodeInfo(target.getParent());
		targetParentReference.id = targetNodeInfo == null ? null : targetNodeInfo.getId();
		targetParentReference.driveId = targetNodeInfo == null ? null : targetNodeInfo.getDriveId();
		targetItem.parentReference = targetParentReference;

		OnedriveIdCache.NodeInfo sourceNodeInfo = requireNodeInfo(source);
		DriveItem movedItem = drive(sourceNodeInfo.getDriveId())//
				.items(sourceNodeInfo.getId()) //
				.buildRequest() //
				.patch(targetItem);
		removeNodeInfo(source);
		return cacheNodeInfo(OnedriveCloudNodeFactory.from(target.getParent(), movedItem), movedItem);
	}

	public OnedriveFile write(final OnedriveFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, final long size) throws BackendException {
		if (exists(file) && !replace) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		String uploadMode = NON_REPLACING_MODE;
		if (replace) {
			uploadMode = REPLACE_MODE;
		}
		final Option conflictBehaviorOption = new QueryOption("@name.conflictBehavior", uploadMode);
		final CompletableFuture<DriveItem> result = new CompletableFuture<>();
		if (size <= CHUNKED_UPLOAD_MAX_SIZE) {
			uploadFile(file, data, progressAware, result, conflictBehaviorOption);
		} else {
			try {
				chunkedUploadFile(file, data, progressAware, result, conflictBehaviorOption, size);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));
		try {
			return OnedriveCloudNodeFactory.file(file.getParent(), result.get(), Optional.of(new Date()));
		} catch (ExecutionException | InterruptedException e) {
			throw new FatalBackendException(e);
		}
	}

	private void uploadFile( //
			final OnedriveFile file, //
			DataSource data, //
			final ProgressAware<UploadState> progressAware, //
			final CompletableFuture<DriveItem> result, //
			Option conflictBehaviorOption) throws NoSuchCloudFileException {
		OnedriveIdCache.NodeInfo parentNodeInfo = requireNodeInfo(file.getParent());
		try (InputStream in = data.open(context)) {
			drive(parentNodeInfo.getDriveId()) //
					.items(parentNodeInfo.getId())//
					.itemWithPath(file.getName()) //
					.content() //
					.buildRequest(singletonList(conflictBehaviorOption)) //
					.put(toByteArray(in), new IProgressCallback<DriveItem>() {
						@Override
						public void progress(long current, long max) {
							progressAware //
									.onProgress(Progress.progress(UploadState.upload(file)) //
											.between(0) //
											.and(max) //
											.withValue(current));
						}

						@Override
						public void success(DriveItem item) {
							progressAware.onProgress(Progress.completed(UploadState.upload(file)));
							result.complete(item);
							cacheNodeInfo(file, item);
						}

						@Override
						public void failure(com.microsoft.graph.core.ClientException ex) {
							result.fail(ex);
						}
					});
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private void chunkedUploadFile( //
			final OnedriveFile file, //
			DataSource data, //
			final ProgressAware<UploadState> progressAware, //
			final CompletableFuture<DriveItem> result, //
			Option conflictBehaviorOption, //
			long size) throws IOException, NoSuchCloudFileException {
		OnedriveIdCache.NodeInfo parentNodeInfo = requireNodeInfo(file.getParent());
		UploadSession uploadSession = drive(parentNodeInfo.getDriveId()) //
				.items(parentNodeInfo.getId()) //
				.itemWithPath(file.getName()) //
				.createUploadSession(new DriveItemUploadableProperties()) //
				.buildRequest() //
				.post();

		try (InputStream in = data.open(context)) {
			new ChunkedUploadProvider<>(uploadSession, client(), in, size, DriveItem.class) //
					.upload(singletonList(conflictBehaviorOption), new IProgressCallback<DriveItem>() {
						@Override
						public void progress(long current, long max) {
							progressAware.onProgress(Progress //
									.progress(UploadState.upload(file)) //
									.between(0) //
									.and(max) //
									.withValue(current));
						}

						@Override
						public void success(DriveItem item) {
							progressAware.onProgress(Progress.completed(UploadState.upload(file)));
							result.complete(item);
							cacheNodeInfo(file, item);
						}

						@Override
						public void failure(com.microsoft.graph.core.ClientException ex) {
							result.fail(ex);
						}
					}, CHUNKED_UPLOAD_CHUNK_SIZE, CHUNKED_UPLOAD_MAX_ATTEMPTS);
		}
	}

	public void read(final OnedriveFile file, final Optional<File> encryptedTmpFile, final OutputStream data, final ProgressAware<DownloadState> progressAware) throws BackendException, IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		OnedriveIdCache.NodeInfo nodeInfo = requireNodeInfo(file);

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			cacheKey = Optional.of(nodeInfo.getId() + nodeInfo.getcTag());
			java.io.File cachedFile = diskLruCache.get(cacheKey.get());
			cacheFile = cachedFile != null ? Optional.of(cachedFile) : Optional.empty();
		}

		if (sharedPreferencesHandler.useLruCache() && cacheFile.isPresent()) {
			try {
				retrieveFromLruCache(cacheFile.get(), data);
			} catch (IOException e) {
				Timber.tag("OnedriveImpl").w(e, "Error while retrieving content from Cache, get from web request");
				writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware);
			}
		} else {
			writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware);
		}
	}

	private void writeToData(final OnedriveFile file, //
			final OnedriveIdCache.NodeInfo nodeInfo, //
			final OutputStream data, //
			final Optional<File> encryptedTmpFile, //
			final Optional<String> cacheKey, //
			final ProgressAware<DownloadState> progressAware) throws IOException {

		final IDriveItemContentStreamRequest request = drive(nodeInfo.getDriveId()) //
				.items(nodeInfo.getId()) //
				.content() //
				.buildRequest();

		try (InputStream in = request.get(); //
				TransferredBytesAwareOutputStream out = new TransferredBytesAwareOutputStream(data) {
					@Override
					public void bytesTransferred(long transferred) {
						progressAware.onProgress( //
								progress(DownloadState.download(file)) //
										.between(0) //
										.and(file.getSize().orElse(Long.MAX_VALUE)) //
										.withValue(transferred));
					}
				}) {
			copyStreamToStream(in, out);
		}

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("OnedriveImpl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	private boolean createLruCache(int cacheSize) {
		if (diskLruCache == null) {
			try {
				diskLruCache = DiskLruCache.create(new LruFileCacheUtil(context).resolve(ONEDRIVE), cacheSize);
			} catch (IOException e) {
				Timber.tag("OnedriveImpl").e(e, "Failed to setup LRU cache");
				return false;
			}
		}

		return true;
	}

	public void delete(OnedriveNode node) throws NoSuchCloudFileException {
		OnedriveIdCache.NodeInfo nodeInfo = requireNodeInfo(node);
		drive(nodeInfo.getDriveId()) //
				.items(nodeInfo.getId()) //
				.buildRequest() //
				.delete();
		removeNodeInfo(node);
	}

	private OnedriveIdCache.NodeInfo requireNodeInfo(OnedriveNode node) throws NoSuchCloudFileException {
		OnedriveIdCache.NodeInfo result = nodeInfo(node);
		if (result == null) {
			throw new NoSuchCloudFileException(node.getPath());
		}
		return result;
	}

	@Nullable
	private OnedriveIdCache.NodeInfo nodeInfo(OnedriveNode node) {
		OnedriveIdCache.NodeInfo result = nodeInfoCache.get(node.getPath());
		if (result == null) {
			result = loadNodeInfo(node);
			if (result == null) {
				return null;
			} else {
				nodeInfoCache.add(node.getPath(), result);
			}
		}
		if (result.isFolder() != node.isFolder()) {
			return null;
		}
		return result;
	}

	private <T extends OnedriveNode> T cacheNodeInfo(T node, DriveItem item) {
		nodeInfoCache.add( //
				node.getPath(), new OnedriveIdCache.NodeInfo( //
						OnedriveCloudNodeFactory.getId(item), //
						OnedriveCloudNodeFactory.getDriveId(item), //
						OnedriveCloudNodeFactory.isFolder(item), //
						item.cTag //
				) //
		);
		return node;
	}

	private void removeNodeInfo(OnedriveNode node) {
		nodeInfoCache.remove(node.getPath());
	}

	private void removeChildNodeInfo(OnedriveFolder folder) {
		nodeInfoCache.removeChildren(folder.getPath());
	}

	private OnedriveIdCache.NodeInfo loadNodeInfo(OnedriveNode node) {
		if (node.getParent() == null) {
			return loadRootNodeInfo();
		} else {
			return loadNonRootNodeInfo(node);
		}
	}

	private OnedriveIdCache.NodeInfo loadRootNodeInfo() {
		DriveItem item = drive(null).root().buildRequest().get();
		return new OnedriveIdCache.NodeInfo( //
				OnedriveCloudNodeFactory.getId(item), //
				OnedriveCloudNodeFactory.getDriveId(item), //
				true, //
				item.cTag //
		);
	}

	private OnedriveIdCache.NodeInfo loadNonRootNodeInfo(OnedriveNode node) {
		OnedriveIdCache.NodeInfo parentNodeInfo = nodeInfo(node.getParent());
		if (parentNodeInfo == null) {
			return null;
		}
		DriveItem item = childByName(parentNodeInfo.getId(), parentNodeInfo.getDriveId(), node.getName());

		if (item == null) {
			return null;
		} else {
			String cTag = item.cTag;

			return new OnedriveIdCache.NodeInfo( //
					OnedriveCloudNodeFactory.getId(item), //
					OnedriveCloudNodeFactory.getDriveId(item), //
					OnedriveCloudNodeFactory.isFolder(item), //
					cTag //
			);
		}
	}

	public String currentAccount() {
		return client().me().drive().buildRequest().get().owner.user.displayName;
	}

	public void logout() {
		final CompletableFuture<Void> result = new CompletableFuture<>();
		clientFactory.getAuthenticationAdapter().logout(new ICallback<Void>() {
			@Override
			public void success(Void aVoid) {
				result.complete(null);
			}

			@Override
			public void failure(ClientException e) {
				result.fail(e);
			}
		});
		try {
			result.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new FatalBackendException(e);
		}
	}
}
