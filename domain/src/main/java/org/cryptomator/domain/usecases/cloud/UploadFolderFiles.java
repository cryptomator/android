package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentChange;
import org.cryptomator.domain.repository.VaultDocumentChangeResult;
import org.cryptomator.domain.repository.VaultDocumentImport;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.io.File.createTempFile;

@UseCase
public class UploadFolderFiles {

	private static final long WAIT_STEP_MILLIS = 500;

	private final Context context;
	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final VaultRepository vaultRepository;
	private final CloudFolder parent;
	private final UploadFolderStructure folderStructure;
	private Set<String> completedFiles = Collections.emptySet();
	private FileUploadedCallback fileUploadedCallback = FileUploadedCallback.NO_OP;

	private volatile boolean cancelled;
	private volatile VaultDocumentImport runningImport;
	private final Flag cancelledFlag = () -> cancelled;

	public UploadFolderFiles(Context context, //
			CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			VaultRepository vaultRepository, //
			@Parameter CloudFolder parent, //
			@Parameter UploadFolderStructure folderStructure) {
		this.context = context;
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.vaultRepository = vaultRepository;
		this.parent = parent;
		this.folderStructure = folderStructure;
	}

	public void setCompletedFiles(Set<String> completedFiles) {
		this.completedFiles = completedFiles;
	}

	public void setFileUploadedCallback(FileUploadedCallback fileUploadedCallback) {
		this.fileUploadedCallback = fileUploadedCallback;
	}

	/**
	 * Cancels the running import and the uploads still to come, so that {@link #execute(ProgressAware)} throws a
	 * {@link CancellationException}, also when it is called afterwards. Uploaded files and created folders are kept.
	 */
	public void onCancel() {
		cancelled = true;
		VaultDocumentImport running = runningImport;
		if (running != null) {
			running.cancel();
		}
	}

	public List<CloudFile> execute(ProgressAware<UploadState> progressAware) throws BackendException {
		try {
			return uploadFolder(parent, folderStructure, progressAware);
		} catch (BackendException | RuntimeException e) {
			if (cancelled) {
				throw new CancellationException(e);
			} else {
				throw e;
			}
		}
	}

	private List<CloudFile> uploadFolder(CloudFolder targetParent, UploadFolderStructure structure, ProgressAware<UploadState> progressAware) throws BackendException {
		return uploadFolder(targetParent, structure, structure.getFolderName(), progressAware);
	}

	private List<CloudFile> uploadFolder(CloudFolder targetParent, UploadFolderStructure structure, String relativePath, ProgressAware<UploadState> progressAware) throws BackendException {
		throwIfCancelled();
		CloudFolder createdFolder = createFolderSafe(targetParent, structure.getFolderName());

		List<CloudFile> uploadedFiles = new ArrayList<>();

		for (UploadFile file : structure.getFiles()) {
			String fileRelativePath = relativePath + "/" + file.getFileName();
			if (completedFiles.contains(fileRelativePath)) {
				continue;
			}
			throwIfCancelled();
			try {
				uploadedFiles.add(upload(createdFolder, file, progressAware));
			} catch (CloudNodeAlreadyExistsException e) {
				// skipped, the file is there already
			}
			fileUploadedCallback.onFileUploaded(fileRelativePath);
		}

		for (UploadFolderStructure subfolder : structure.getSubfolders()) {
			String subfolderRelativePath = relativePath + "/" + subfolder.getFolderName();
			uploadedFiles.addAll(uploadFolder(createdFolder, subfolder, subfolderRelativePath, progressAware));
		}

		return uploadedFiles;
	}

	private void throwIfCancelled() {
		if (cancelled) {
			throw new CancellationException();
		}
	}

	private CloudFolder createFolderSafe(CloudFolder parent, String folderName) throws BackendException {
		try {
			VaultPath vaultFolder = vaultDocumentRepository.currentPathOf(parent);
			if (vaultFolder != null) {
				return awaitChange(vaultFolder, vaultDocumentRepository.createFolder(vaultFolder, folderName, false));
			}
			return cloudContentRepository.create( //
					cloudContentRepository.folder(parent, folderName));
		} catch (CloudNodeAlreadyExistsException e) {
			return cloudContentRepository.folder(parent, folderName);
		}
	}

	private CloudFile upload(CloudFolder folder, UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.currentPathOf(folder);
		if (vaultFolder != null) {
			return importIntoVault(vaultFolder, uploadFile, progressAware);
		}
		DataSource dataSource = uploadFile.getDataSource();
		if (dataSource.size(context) != null) {
			return upload(folder, uploadFile, dataSource, progressAware);
		} else {
			File file = copyDataToFile(dataSource);
			try {
				return upload(folder, uploadFile, FileBasedDataSource.from(file), progressAware);
			} finally {
				file.delete();
			}
		}
	}

	private CloudFile importIntoVault(VaultPath folder, UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
		DataSource dataSource = CancelAwareDataSource.wrap(uploadFile.getDataSource(), cancelledFlag);
		VaultDocumentImport vaultImport = vaultDocumentRepository.importFile(folder, uploadFile.getFileName(), dataSource, uploadFile.getReplacing(), false, false, progressAware);
		runningImport = vaultImport;
		if (cancelled) {
			vaultImport.cancel();
		}
		try {
			return awaitChange(folder, vaultImport);
		} finally {
			runningImport = null;
		}
	}

	/**
	 * Waits for a change of the vault cache to reach the cloud, but stops waiting when the upload is canceled or the vault
	 * is locked, which keeps the change pending until the next unlock. The change goes on in the cache either way.
	 */
	private <T> T awaitChange(VaultPath path, VaultDocumentChange<T> change) throws BackendException {
		while (true) {
			VaultDocumentChangeResult<T> result;
			try {
				result = change.await(WAIT_STEP_MILLIS, TimeUnit.MILLISECONDS);
			} catch (BackendException | RuntimeException e) {
				// locking the vault stops a running upload of the cache like a cancellation, or fails it when the lock destroys
				// the cryptor while encrypting
				vaultRepository.assertUnlocked(path.getVault());
				throw e;
			}
			if (result != null) {
				return result.getNode();
			}
			throwIfCancelled();
			vaultRepository.assertUnlocked(path.getVault());
		}
	}

	private CloudFile upload(CloudFolder folder, UploadFile uploadFile, DataSource dataSource, ProgressAware<UploadState> progressAware) throws BackendException {
		return writeCloudFile( //
				folder, //
				uploadFile.getFileName(), //
				CancelAwareDataSource.wrap(dataSource, cancelledFlag), //
				uploadFile.getReplacing(), //
				progressAware);
	}

	private CloudFile writeCloudFile(CloudFolder folder, String fileName, CancelAwareDataSource dataSource, boolean replacing, ProgressAware<UploadState> progressAware) throws BackendException {
		Long size = dataSource.size(context);
		CloudFile source = cloudContentRepository.file(folder, fileName, size);
		return cloudContentRepository.write( //
				source, //
				dataSource, //
				progressAware, //
				replacing, //
				size);
	}

	private File copyDataToFile(DataSource dataSource) {
		File dir = context.getCacheDir();
		try {
			File target = createTempFile("upload", "tmp", dir);
			try {
				InputStream in = CancelAwareDataSource.wrap(dataSource, cancelledFlag).open(context);
				OutputStream out = new FileOutputStream(target);
				StreamHelper.copy(in, out);
				dataSource.modifiedDate(context).ifPresent(value -> target.setLastModified(value.getTime()));
				return target;
			} catch (IOException e) {
				target.delete();
				throw e;
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

}
