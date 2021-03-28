package org.cryptomator.data.cloud.local.storageaccessframework;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import org.cryptomator.data.util.TransferredBytesAwareInputStream;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NotFoundException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;
import org.cryptomator.util.file.MimeType;
import org.cryptomator.util.file.MimeTypes;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import timber.log.Timber;

import static org.cryptomator.data.cloud.local.storageaccessframework.LocalStorageAccessFrameworkNodeFactory.from;
import static org.cryptomator.data.util.CopyStream.closeQuietly;
import static org.cryptomator.data.util.CopyStream.copyStreamToStream;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class LocalStorageAccessFrameworkImpl {

	private final Context context;
	private final RootLocalStorageAccessFolder root;
	private final DocumentIdCache idCache;
	private final MimeTypes mimeTypes;

	LocalStorageAccessFrameworkImpl(Context context, MimeTypes mimeTypes, LocalStorageCloud cloud, DocumentIdCache documentIdCache) {
		this.mimeTypes = mimeTypes;
		if (!hasUriPermissions(context, cloud.rootUri())) {
			throw new NoAuthenticationProvidedException(cloud);
		}
		this.context = context;
		this.root = new RootLocalStorageAccessFolder(cloud);
		this.idCache = documentIdCache;
	}

	private boolean hasUriPermissions(Context context, String uri) {
		Optional<UriPermission> uriPermission = uriPermissionFor(context, uri);
		return uriPermission.isPresent() //
				&& uriPermission.get().isReadPermission() //
				&& uriPermission.get().isWritePermission();
	}

	private Optional<UriPermission> uriPermissionFor(Context context, String uri) {
		for (UriPermission uriPermission : context.getContentResolver().getPersistedUriPermissions()) {
			if (uri.equals(uriPermission.getUri().toString())) {
				return Optional.of(uriPermission);
			}
		}
		return Optional.empty();
	}

	public LocalStorageAccessFolder root() {
		return root;
	}

	public LocalStorageAccessFolder resolve(String path) throws BackendException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}

		String[] names = path.split("/");
		LocalStorageAccessFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name) throws BackendException {
		return file( //
				parent, //
				name, //
				Optional.empty());
	}

	public LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name, Optional<Long> size) throws BackendException {
		if (parent.getDocumentId() == null) {
			return LocalStorageAccessFrameworkNodeFactory.file( //
					parent, //
					name, //
					size);
		}
		String path = LocalStorageAccessFrameworkNodeFactory.getNodePath(parent, name);
		DocumentIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && !nodeInfo.isFolder()) {
			return LocalStorageAccessFrameworkNodeFactory.file( //
					parent, //
					name, //
					path, //
					size, //
					nodeInfo.getId());
		}
		List<LocalStorageAccessNode> cloudNodes = listFilesWithNameFilter(parent, name);
		if (cloudNodes.size() > 0) {
			LocalStorageAccessNode cloudNode = cloudNodes.get(0);
			if (cloudNode instanceof LocalStorageAccessFile) {
				return idCache.cache((LocalStorageAccessFile) cloudNode);
			}
		}
		return LocalStorageAccessFrameworkNodeFactory.file( //
				parent, //
				name, //
				size);
	}

	public LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, String name) throws BackendException {
		if (parent.getDocumentId() == null) {
			return LocalStorageAccessFrameworkNodeFactory.folder( //
					parent, //
					name);
		}
		String path = LocalStorageAccessFrameworkNodeFactory.getNodePath(parent, name);
		DocumentIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && nodeInfo.isFolder()) {
			return LocalStorageAccessFrameworkNodeFactory.folder( //
					parent, //
					name, //
					nodeInfo.getId());
		}
		List<LocalStorageAccessNode> cloudNodes = listFilesWithNameFilter(parent, name);
		if (cloudNodes.size() > 0) {
			LocalStorageAccessNode cloudNode = cloudNodes.get(0);
			if (cloudNode instanceof LocalStorageAccessFolder) {
				return idCache.cache((LocalStorageAccessFolder) cloudNode);
			}
		}

		return LocalStorageAccessFrameworkNodeFactory.folder( //
				parent, //
				name);
	}

	private List<LocalStorageAccessNode> listFilesWithNameFilter(LocalStorageAccessFolder parent, String name) throws BackendException {
		if (parent.getUri() == null) {
			List<LocalStorageAccessNode> parents = listFilesWithNameFilter(parent.getParent(), parent.getName());
			if (parents.isEmpty() || !(parents.get(0) instanceof LocalStorageAccessFolder)) {
				throw new NoSuchCloudFileException(name);
			}
			parent = (LocalStorageAccessFolder) parents.get(0);
		}
		Cursor childCursor = null;
		try {
			childCursor = contentResolver() //
					.query( //
							DocumentsContract.buildChildDocumentsUriUsingTree( //
									parent.getUri(), //
									parent.getDocumentId()), //
							new String[] {Document.COLUMN_DISPLAY_NAME, // cursor position 0
									Document.COLUMN_MIME_TYPE, // cursor position 1
									Document.COLUMN_SIZE, // cursor position 2
									Document.COLUMN_LAST_MODIFIED, // cursor position 3
									Document.COLUMN_DOCUMENT_ID // cursor position 4
							}, //
							null, //
							null, //
							null);

			List<LocalStorageAccessNode> result = new ArrayList<>();
			while (childCursor != null && childCursor.moveToNext()) {
				if (childCursor.getString(0).equals(name)) {
					result.add(idCache.cache(from(parent, childCursor)));
				}
			}
			return result;
		} catch (IllegalArgumentException e) {
			if (e.getMessage().contains(FileNotFoundException.class.getCanonicalName())) {
				throw new NoSuchCloudFileException(name);
			}
			throw new FatalBackendException(e);
		} finally {
			closeQuietly(childCursor);
		}
	}

	public boolean exists(LocalStorageAccessNode node) throws BackendException {
		try {

			List<LocalStorageAccessNode> cloudNodes = listFilesWithNameFilter( //
					node.getParent(), //
					node.getName());

			boolean documentExists = cloudNodes.size() > 0;

			if (documentExists) {
				idCache.add(cloudNodes.get(0));
			}

			return documentExists;
		} catch (NoSuchCloudFileException e) {
			return false;
		}
	}

	public List<CloudNode> list(LocalStorageAccessFolder folder) throws BackendException {
		Cursor childCursor = contentResolver() //
				.query( //
						DocumentsContract.buildChildDocumentsUriUsingTree( //
								folder.getUri(), //
								folder.getDocumentId()), //
						new String[] { //
								Document.COLUMN_DISPLAY_NAME, // cursor position 0
								Document.COLUMN_MIME_TYPE, // cursor position 1
								Document.COLUMN_SIZE, // cursor position 2
								Document.COLUMN_LAST_MODIFIED, // cursor position 3
								Document.COLUMN_DOCUMENT_ID // cursor position 4
						}, null, null, null);

		try {
			List<CloudNode> result = new ArrayList<>();
			while (childCursor != null && childCursor.moveToNext()) {
				result.add(idCache.cache(from(folder, childCursor)));
			}
			return result;
		} finally {
			closeQuietly(childCursor);
		}
	}

	public LocalStorageAccessFolder create(LocalStorageAccessFolder folder) throws BackendException {
		if (folder //
				.getParent() //
				.getDocumentId() == null) {
			folder = new LocalStorageAccessFolder( //
					create(folder.getParent()), //
					folder.getName(), //
					folder.getPath(), //
					null, //
					null);
		}
		Uri createdDocument;
		try {
			createdDocument = DocumentsContract.createDocument( //
					contentResolver(), //
					folder.getParent().getUri(), //
					Document.MIME_TYPE_DIR, //
					folder.getName());
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(folder.getName());
		}
		return idCache.cache( //
				LocalStorageAccessFrameworkNodeFactory.folder( //
						folder.getParent(), //
						buildDocumentFile(createdDocument)));
	}

	public LocalStorageAccessNode move(LocalStorageAccessNode source, LocalStorageAccessNode target) throws BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		idCache.remove(source);
		idCache.remove(target);
		boolean isRename = !source //
				.getName() //
				.equals(target.getName());
		boolean isMove = !source //
				.getParent() //
				.equals(target.getParent());
		LocalStorageAccessNode renamedSource = source;
		if (isRename) {
			renamedSource = rename(source, target.getName());
		}
		if (isMove) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				return idCache.cache( //
						moveForApiStartingFrom24(renamedSource, target));
			} else {
				return idCache.cache( //
						moveForApiBelow24(renamedSource, target));
			}
		}
		return renamedSource;
	}

	private LocalStorageAccessNode rename(LocalStorageAccessNode source, String name) throws NoSuchCloudFileException {
		Uri newUri = null;
		try {
			newUri = DocumentsContract.renameDocument( //
					contentResolver(), //
					source.getUri(), //
					name);
		} catch (FileNotFoundException e) {
			// Bug in Android 9 see #460
			if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
				throw new NoSuchCloudFileException(source.getName());
			}
		}

		// Bug in Android 9 see #460
		if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
			try {
				List<LocalStorageAccessNode> cloudNodes = listFilesWithNameFilter( //
						source.getParent(), //
						name);

				newUri = cloudNodes.get(0).getUri();
			} catch (BackendException e) {
				Timber.tag("LocalStgeAccessFrkImpl").e(e);
			}
		}

		return LocalStorageAccessFrameworkNodeFactory.from( //
				source.getParent(), //
				buildDocumentFile(newUri));
	}

	@RequiresApi(api = Build.VERSION_CODES.N)
	private LocalStorageAccessNode moveForApiStartingFrom24(LocalStorageAccessNode source, LocalStorageAccessNode target) throws NoSuchCloudFileException {
		Uri movedTargetUri;
		try {
			movedTargetUri = DocumentsContract.moveDocument( //
					contentResolver(), //
					source.getUri(), //
					source.getParent().getUri(), //
					target.getParent().getUri());
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(source.getName());
		}
		return from( //
				target.getParent(), //
				buildDocumentFile(movedTargetUri));
	}

	private LocalStorageAccessNode moveForApiBelow24(LocalStorageAccessNode source, LocalStorageAccessNode target) throws BackendException {
		try {
			LocalStorageAccessNode result;
			if (source instanceof CloudFolder) {
				result = moveForApiBelow24( //
						(LocalStorageAccessFolder) source, //
						(LocalStorageAccessFolder) target);
			} else {
				result = moveForApiBelow24( //
						(LocalStorageAccessFile) source, //
						(LocalStorageAccessFile) target);
			}
			delete(source);
			return result;
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private LocalStorageAccessFolder moveForApiBelow24(LocalStorageAccessFolder source, LocalStorageAccessFolder target) throws IOException, BackendException {
		if (!exists(target.getParent())) {
			throw new NoSuchCloudFileException(target.getParent().getPath());
		}
		LocalStorageAccessFolder createdFolder = create(target);
		for (CloudNode child : list(source)) {
			if (child instanceof CloudFolder) {
				moveForApiBelow24( //
						(LocalStorageAccessFolder) child, //
						folder(target, child.getName()));
			} else {
				moveForApiBelow24( //
						(LocalStorageAccessFile) child, //
						file(target, child.getName()));
			}
		}
		return createdFolder;
	}

	private LocalStorageAccessFile moveForApiBelow24(final LocalStorageAccessFile source, LocalStorageAccessFile target) throws IOException, BackendException {
		DataSource dataSource = new DataSource() {
			@Override
			public void close() throws IOException {
				// do nothing
			}

			@Override
			public Optional<Long> size(Context context) {
				return source.getSize();
			}

			@Override
			public Optional<Date> modifiedDate(Context context) {
				return source.getModified();
			}

			@Override
			public InputStream open(Context context) throws IOException {
				return contentResolver().openInputStream(source.getUri());
			}

			@Override
			public DataSource decorate(DataSource delegate) {
				return delegate;
			}
		};
		return write(target, dataSource, NO_OP_PROGRESS_AWARE, true, source.getSize().get());
	}

	public LocalStorageAccessFile write( //
			LocalStorageAccessFile file, //
			final DataSource data, //
			final ProgressAware<UploadState> progressAware, //
			final boolean replace, //
			final long size) throws IOException, BackendException {

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		Optional<Uri> fileUri = existingFileUri(file);
		if (fileUri.isPresent() && !replace) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		if (file.getParent().getUri() == null) {
			LocalStorageAccessFolder parent = (LocalStorageAccessFolder) listFilesWithNameFilter(file.getParent().getParent(), file.getParent().getName()).get(0);
			String tmpFileUri = fileUri.isPresent() ? fileUri.get().toString() : "";
			file = new LocalStorageAccessFile(parent, file.getName(), file.getPath(), file.getSize(), file.getModified(), file.getDocumentId(), tmpFileUri);
		}

		final LocalStorageAccessFile tmpFile = file;

		Uri uploadUri = fileUri.orElseGet(createNewDocumentSupplier(tmpFile));
		if (uploadUri == null) {
			throw new NotFoundException(tmpFile.getName());
		}

		try (OutputStream out = contentResolver().openOutputStream(uploadUri); //
			 TransferredBytesAwareInputStream in = new TransferredBytesAwareInputStream(data.open(context)) {
				 @Override
				 public void bytesTransferred(long transferred) {
					 progressAware //
							 .onProgress(progress(UploadState.upload(tmpFile)) //
									 .between(0) //
									 .and(size) //
									 .withValue(transferred));
				 }
			 }) {
			if (out instanceof FileOutputStream) {
				((FileOutputStream) out).getChannel().truncate(0);
			}

			copyStreamToStream(in, out);
		}

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return LocalStorageAccessFrameworkNodeFactory.file( //
				file.getParent(), //
				buildDocumentFile(uploadUri));
	}

	private Supplier<Uri> createNewDocumentSupplier(final LocalStorageAccessFile file) {
		return () -> {
			MimeType mimeType = mimeTypes.fromFilename(file.getName()) //
					.orElse(MimeType.APPLICATION_OCTET_STREAM);
			try {
				return DocumentsContract.createDocument( //
						contentResolver(), //
						file.getParent().getUri(), //
						mimeType.toString(), //
						file.getName());
			} catch (FileNotFoundException e) {
				return null;
			}
		};
	}

	private Optional<Uri> existingFileUri(LocalStorageAccessFile file) throws BackendException {
		List<LocalStorageAccessNode> nodes = listFilesWithNameFilter( //
				file.getParent(), //
				file.getName());
		if (nodes.size() > 0) {
			return Optional.of(nodes.get(0).getUri());
		} else {
			return Optional.empty();
		}
	}

	public void read(final LocalStorageAccessFile file, final OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		try (InputStream in = contentResolver().openInputStream(file.getUri()); //
			 TransferredBytesAwareOutputStream out = new TransferredBytesAwareOutputStream(data) {
				 @Override
				 public void bytesTransferred(long transferred) {
					 progressAware.onProgress(progress(DownloadState.download(file)) //
							 .between(0) //
							 .and(file.getSize().orElse(Long.MAX_VALUE)) //
							 .withValue(transferred));
				 }
			 }) {
			copyStreamToStream(in, out);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	public void delete(LocalStorageAccessNode node) throws NoSuchCloudFileException {
		try {
			DocumentsContract.deleteDocument( //
					contentResolver(), //
					node.getUri());
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(node.getName());
		}
		idCache.remove(node);
	}

	private DocumentFile buildDocumentFile(Uri fileUri) {
		return DocumentFile.fromSingleUri(context, fileUri);
	}

	private ContentResolver contentResolver() {
		return context.getContentResolver();
	}
}
