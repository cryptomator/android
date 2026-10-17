package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentImport;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static java.io.File.createTempFile;

@UseCase
class UploadFiles {

	private static final int EOF = -1;

	private final Context context;
	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder parent;
	private final List<UploadFile> files;
	private final boolean optimistic;

	private volatile boolean cancelled;
	private volatile VaultDocumentImport runningImport;
	private final Flag cancelledFlag = new Flag() {
		@Override
		public boolean get() {
			return cancelled;
		}
	};

	public UploadFiles(Context context, //
			CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			@Parameter CloudFolder parent, //
			@Parameter List<UploadFile> files, //
			@Optional @Parameter Boolean optimistic) {
		this.context = context;
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.parent = parent;
		this.files = files;
		this.optimistic = Boolean.TRUE.equals(optimistic);
	}

	/**
	 * Cancels the import that is running, if {@link #execute(ProgressAware)} is. Imports that are done are kept.
	 */
	public void onCancel() {
		cancelled = true;
		VaultDocumentImport running = runningImport;
		if (running != null) {
			running.cancel();
		}
	}

	public List<CloudFile> execute(ProgressAware<UploadState> progressAware) throws BackendException {
		cancelled = false;
		try {
			return upload(progressAware);
		} catch (BackendException | RuntimeException e) {
			if (cancelled) {
				throw new CancellationException(e);
			} else {
				throw e;
			}
		}
	}

	private List<CloudFile> upload(ProgressAware<UploadState> progressAware) throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(parent);
		List<CloudFile> uploadedFiles = new ArrayList<>();
		for (UploadFile file : files) {
			CloudFile uploaded = vaultFolder != null ? importIntoVault(vaultFolder, file, progressAware) : upload(file, progressAware);
			// an optimistic upload of a file that has been deleted meanwhile has no result
			if (uploaded != null) {
				uploadedFiles.add(uploaded);
			}
		}
		return uploadedFiles;
	}

	private CloudFile importIntoVault(VaultPath folder, UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
		DataSource dataSource = CancelAwareDataSource.wrap(uploadFile.getDataSource(), cancelledFlag);
		// an optimistic upload goes on after execute() has returned, so its progress only reaches the listeners
		ProgressAware<UploadState> importProgress = optimistic ? ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD : progressAware;
		VaultDocumentImport vaultImport = vaultDocumentRepository.importFile(folder, uploadFile.getFileName(), dataSource, uploadFile.getReplacing(), false, optimistic, importProgress);
		runningImport = vaultImport;
		if (cancelled) {
			vaultImport.cancel();
		}
		try {
			return optimistic ? vaultImport.cachedNode() : vaultImport.await().getNode();
		} finally {
			runningImport = null;
		}
	}

	private CloudFile upload(UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
		DataSource dataSource = uploadFile.getDataSource();
		if (dataSource.size(context) != null) {
			return upload(uploadFile, dataSource, progressAware);
		} else {
			File file = copyDataToFile(dataSource);
			try {
				return upload(uploadFile, FileBasedDataSource.from(file), progressAware);
			} finally {
				file.delete();
			}
		}
	}

	private CloudFile upload(UploadFile uploadFile, DataSource dataSource, ProgressAware<UploadState> progressAware) throws BackendException {
		return writeCloudFile( //
				uploadFile.getFileName(), //
				CancelAwareDataSource.wrap(dataSource, cancelledFlag), //
				uploadFile.getReplacing(), //
				progressAware);
	}

	private File copyDataToFile(DataSource dataSource) {
		File dir = context.getCacheDir();
		try {
			File target = createTempFile("upload", "tmp", dir);
			InputStream in = CancelAwareDataSource.wrap(dataSource, cancelledFlag).open(context);
			OutputStream out = new FileOutputStream(target);
			copy(in, out);
			dataSource.modifiedDate(context).ifPresent(value -> target.setLastModified(value.getTime()));
			return target;
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private CloudFile writeCloudFile(String fileName, CancelAwareDataSource dataSource, boolean replacing, ProgressAware<UploadState> progressAware) throws BackendException {
		Long size = dataSource.size(context);
		CloudFile source = cloudContentRepository.file(parent, fileName, size);
		return cloudContentRepository.write( //
				source, //
				dataSource, //
				progressAware, //
				replacing, //
				size);
	}

	private void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		try {
			while (copyDidNotReachEof(in, out, buffer)) {
				// empty
			}
		} finally {
			closeQuietly(in);
			closeQuietly(out);
		}
	}

	private boolean copyDidNotReachEof(InputStream in, OutputStream out, byte[] buffer) throws IOException {
		int read = in.read(buffer);
		if (read == EOF) {
			return false;
		} else {
			out.write(buffer, 0, read);
			return true;
		}
	}

	private void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

}
