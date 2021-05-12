package org.cryptomator.data.cloud.s3;

import android.content.Context;

import org.cryptomator.data.util.CopyStream;
import org.cryptomator.data.util.TransferredBytesAwareInputStream;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.ForbiddenException;
import org.cryptomator.domain.exception.NoSuchBucketException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.WrongCredentialsException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import timber.log.Timber;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;

class S3Impl {

	private static final String DELIMITER = "/";

	private final S3ClientFactory clientFactory = new S3ClientFactory();
	private final S3Cloud cloud;
	private final RootS3Folder root;
	private final Context context;

	S3Impl(Context context, S3Cloud cloud) {
		if (cloud.accessKey() == null || cloud.secretKey() == null) {
			throw new WrongCredentialsException(cloud);
		}

		this.context = context;
		this.cloud = cloud;
		this.root = new RootS3Folder(cloud);
	}

	private MinioClient client() {
		return clientFactory.getClient(cloud, context);
	}

	public S3Folder root() {
		return root;
	}

	public S3Folder resolve(String path) {
		if (path.startsWith(DELIMITER)) {
			path = path.substring(1);
		}
		String[] names = path.split(DELIMITER);
		S3Folder folder = root;
		for (String name : names) {
			if (!name.isEmpty()) {
				folder = folder(folder, name);
			}
		}
		return folder;
	}

	public S3File file(S3Folder parent, String name) throws BackendException, IOException {
		return file(parent, name, Optional.empty());
	}

	public S3File file(S3Folder parent, String name, Optional<Long> size) throws BackendException, IOException {
		return S3CloudNodeFactory.file(parent, name, size, parent.getKey() + name);
	}

	public S3Folder folder(S3Folder parent, String name) {
		return S3CloudNodeFactory.folder(parent, name, parent.getKey() + name);
	}

	public boolean exists(S3Node node) throws BackendException {
		String key = node.getKey();
		try {
			if(!(node instanceof RootS3Folder)) {
				client().statObject(StatObjectArgs.builder().bucket(cloud.s3Bucket()).object(key).build());
				return true;
			} else {
				// stat requests throws an IllegalStateException if key is empty string
				ListObjectsArgs request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(key).delimiter(DELIMITER).build();
				return client().listObjects(request).iterator().hasNext();
			}
		} catch (ErrorResponseException e) {
			if (S3CloudApiErrorCodes.NO_SUCH_KEY.getValue().equals(e.errorResponse().code())) {
				return false;
			}
			throw new FatalBackendException(e);
		} catch (Exception ex) {
			handleApiError(ex, node.getPath());
		}

		throw new FatalBackendException(new IllegalStateException("Exception thrown but not handled?"));
	}

	public List<S3Node> list(S3Folder folder) throws IOException, BackendException {
		List<S3Node> result = new ArrayList<>();

		ListObjectsArgs request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(folder.getKey()).delimiter(DELIMITER).build();
		Iterable<Result<Item>> listObjects = client().listObjects(request);
		for (Result<Item> object : listObjects) {
			try {
				Item item = object.get();
				if (item.isDir()) {
					result.add(S3CloudNodeFactory.folder(folder, S3CloudNodeFactory.getNameFromKey(item.objectName())));
				} else {
					S3File file = S3CloudNodeFactory.file(folder, S3CloudNodeFactory.getNameFromKey(item.objectName()), Optional.of(item.size()), Optional.of(Date.from(item.lastModified().toInstant())));
					result.add(file);
				}
			} catch (Exception ex) {
				handleApiError(ex, folder.getPath());
			}
		}

		return result;
	}

	public S3Folder create(S3Folder folder) throws IOException, BackendException {
		if (!exists(folder.getParent())) {
			folder = new S3Folder( //
					create(folder.getParent()), //
					folder.getName(), folder.getPath() //
			);
		}

		try {
			PutObjectArgs putObjectArgs = PutObjectArgs //
					.builder() //
					.bucket(cloud.s3Bucket()) //
					.object(folder.getKey()) //
					.stream(new ByteArrayInputStream(new byte[0]), 0, -1) //
					.build();

			client().putObject(putObjectArgs);
		} catch (Exception ex) {
			handleApiError(ex, folder.getPath());
		}

		return S3CloudNodeFactory.folder(folder.getParent(), folder.getName());
	}

	public S3Node move(S3Node source, S3Node target) throws IOException, BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		if (source instanceof S3Folder) {
			List<S3Node> nodes = list((S3Folder) source);

			List<DeleteObject> objectsToDelete = new LinkedList<>();

			for (S3Node node : nodes) {
				objectsToDelete.add(new DeleteObject(node.getKey()));

				String targetKey;
				if (node instanceof S3Folder) {
					targetKey = S3CloudNodeFactory.folder((S3Folder) target, node.getName()).getKey();
				} else {
					targetKey = S3CloudNodeFactory.file((S3Folder) target, node.getName()).getKey();
				}

				CopySource copySource = CopySource.builder().bucket(cloud.s3Bucket()).object(node.getKey()).build();

				CopyObjectArgs copyObjectArgs = CopyObjectArgs.builder().bucket(cloud.s3Bucket()).object(targetKey).source(copySource).build();
				try {
					client().copyObject(copyObjectArgs);
				} catch (Exception ex) {
					handleApiError(ex, source.getPath());
				}
			}

			RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket(cloud.s3Bucket()).objects(objectsToDelete).build();

			for (Result<DeleteError> result : client().removeObjects(removeObjectsArgs)) {
				try {
					result.get();
				} catch (Exception ex) {
					handleApiError(ex, source.getPath());
				}
			}

			return S3CloudNodeFactory.folder(target.getParent(), target.getName());
		} else {
			CopySource copySource = CopySource.builder().bucket(cloud.s3Bucket()).object(source.getKey()).build();
			CopyObjectArgs copyObjectArgs = CopyObjectArgs.builder().bucket(cloud.s3Bucket()).object(target.getKey()).source(copySource).build();
			try {
				ObjectWriteResponse result = client().copyObject(copyObjectArgs);

				delete(source);

				Date lastModified = result.headers().getDate("Last-Modified");

				return S3CloudNodeFactory.file(target.getParent(), target.getName(), ((S3File) source).getSize(), Optional.ofNullable(lastModified));
			} catch (Exception ex) {
				handleApiError(ex, source.getPath());
			}
		}

		throw new FatalBackendException(new IllegalStateException("Exception thrown but not handled?"));
	}

	public S3File write(S3File file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size) throws IOException, BackendException {
		if (!replace && exists(file)) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));

		try (TransferredBytesAwareDataSource out = new TransferredBytesAwareDataSource(data) {
			@Override
			public void bytesTransferred(long transferred) {
				progressAware.onProgress( //
						progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(transferred));
			}
		}) {
			try {
				PutObjectArgs putObjectArgs = PutObjectArgs.builder().bucket(cloud.s3Bucket()).object(file.getKey()).stream(out.open(context), data.size(context).get(), -1).build();
				ObjectWriteResponse objectWriteResponse = client().putObject(putObjectArgs);

				Date lastModified = objectWriteResponse.headers().getDate("Last-Modified");

				if(lastModified == null) {
					StatObjectResponse statObjectResponse = client().statObject(StatObjectArgs //
							.builder() //
							.bucket(cloud.s3Bucket()) //
							.object(file.getKey()) //
							.build());

					lastModified = Date.from(statObjectResponse.lastModified().toInstant());
				}

				progressAware.onProgress(Progress.completed(UploadState.upload(file)));
				return S3CloudNodeFactory.file(file.getParent(), file.getName(), Optional.of(size), Optional.of(lastModified));
			} catch (Exception ex) {
				handleApiError(ex, file.getPath());
			}
		}

		throw new FatalBackendException(new IllegalStateException("Exception thrown but not handled?"));
	}

	public void read(S3File file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket(cloud.s3Bucket()).object(file.getKey()).build();

		try (GetObjectResponse response = client().getObject(getObjectArgs); //
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
			CopyStream.copyStreamToStream(response, out);
		} catch (Exception ex) {
			handleApiError(ex, file.getPath());
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	public void delete(S3Node node) throws IOException, BackendException {
		if (node instanceof S3Folder) {

			List<DeleteObject> objectsToDelete = new LinkedList<>();

			ListObjectsArgs request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(node.getKey()).recursive(true).delimiter(DELIMITER).build();
			Iterable<Result<Item>> listObjects = client().listObjects(request);
			for (Result<Item> object : listObjects) {
				try {
					Item item = object.get();
					objectsToDelete.add(new DeleteObject(item.objectName()));
				} catch (Exception e) {
					handleApiError(e, node.getPath());
				}
			}

			RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket(cloud.s3Bucket()).objects(objectsToDelete).build();
			Iterable<Result<DeleteError>> results = client().removeObjects(removeObjectsArgs);
			for (Result<DeleteError> result : results) {
				try {
					DeleteError error = result.get();
					Timber.tag("S3Impl").e("Error in deleting object " + error.objectName() + "; " + error.message());
				} catch (Exception e) {
					handleApiError(e, node.getPath());
				}
			}

		} else {
			RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(cloud.s3Bucket()).object(node.getKey()).build();
			try {
				client().removeObject(removeObjectArgs);
			} catch (Exception e) {
				handleApiError(e, "");
			}
		}
	}

	public String checkAuthentication() throws NoSuchBucketException, BackendException {
		try {
			if (!client().bucketExists(BucketExistsArgs.builder().bucket(cloud.s3Bucket()).build())) {
				throw new NoSuchBucketException(cloud.s3Bucket());
			}
		} catch (Exception e) {
			handleApiError(e, "");
		}

		return "";
	}

	private void handleApiError(Exception ex, String name) throws BackendException {
		if (ex instanceof ErrorResponseException) {
			String errorCode = ((ErrorResponseException) ex).errorResponse().code();
			if (S3CloudApiExceptions.isAccessProblem(errorCode)) {
				throw new ForbiddenException();
			} else if (S3CloudApiErrorCodes.NO_SUCH_BUCKET.getValue().equals(errorCode)) {
				throw new NoSuchBucketException(name);
			} else if (S3CloudApiErrorCodes.NO_SUCH_KEY.getValue().equals(errorCode)) {
				throw new NoSuchCloudFileException(name);
			} else {
				throw new FatalBackendException(ex);
			}
		} else {
			throw new FatalBackendException(ex);
		}
	}

	private static abstract class TransferredBytesAwareDataSource implements DataSource {

		private final DataSource data;

		TransferredBytesAwareDataSource(DataSource data) {
			this.data = data;
		}

		@Override
		public Optional<Long> size(Context context) {
			return data.size(context);
		}

		@Override
		public InputStream open(Context context) throws IOException {
			return new TransferredBytesAwareInputStream(data.open(context)) {
				@Override
				public void bytesTransferred(long transferred) {
					S3Impl.TransferredBytesAwareDataSource.this.bytesTransferred(transferred);
				}
			};
		}

		@Override
		public void close() throws IOException {
			data.close();
		}

		public abstract void bytesTransferred(long transferred);

		@Override
		public DataSource decorate(DataSource delegate) {
			return delegate;
		}
	}
}
