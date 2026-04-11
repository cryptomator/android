package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
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

import static java.io.File.createTempFile;

@UseCase
public class UploadFolderFiles {

	private final Context context;
	private final CloudContentRepository cloudContentRepository;
	private final CloudFolder parent;
	private final UploadFolderStructure folderStructure;
	private Set<String> completedFiles = Collections.emptySet();
	private FileUploadedCallback fileUploadedCallback = FileUploadedCallback.NO_OP;
	private FolderCreatedCallback folderCreatedCallback = FolderCreatedCallback.NO_OP;

	private volatile boolean cancelled;
	private final Flag cancelledFlag = () -> cancelled;

	public UploadFolderFiles(Context context, //
			CloudContentRepository cloudContentRepository, //
			@Parameter CloudFolder parent, //
			@Parameter UploadFolderStructure folderStructure) {
		this.context = context;
		this.cloudContentRepository = cloudContentRepository;
		this.parent = parent;
		this.folderStructure = folderStructure;
	}

	public void setCompletedFiles(Set<String> completedFiles) {
		this.completedFiles = completedFiles;
	}

	public void setFileUploadedCallback(FileUploadedCallback fileUploadedCallback) {
		this.fileUploadedCallback = fileUploadedCallback;
	}

	public void setFolderCreatedCallback(FolderCreatedCallback folderCreatedCallback) {
		this.folderCreatedCallback = folderCreatedCallback;
	}

	public void onCancel() {
		cancelled = true;
	}

	public List<CloudFile> execute(ProgressAware<UploadState> progressAware) throws BackendException {
		cancelled = false;
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
		CloudFolder createdFolder = createFolderSafe(targetParent, structure.getFolderName());
		folderCreatedCallback.onFolderCreated(relativePath, createdFolder);

		List<CloudFile> uploadedFiles = new ArrayList<>();

		for (UploadFile file : structure.getFiles()) {
			String fileRelativePath = relativePath + "/" + file.getFileName();
			if (completedFiles.contains(fileRelativePath)) {
				continue;
			}
			try {
				CloudFile uploadedFile = upload(createdFolder, file, progressAware);
				uploadedFiles.add(uploadedFile);
				fileUploadedCallback.onFileUploaded(fileRelativePath, uploadedFile);
			} catch (CloudNodeAlreadyExistsException e) {
				fileUploadedCallback.onFileUploaded(fileRelativePath, null);
			}
		}

		for (UploadFolderStructure subfolder : structure.getSubfolders()) {
			String subfolderRelativePath = relativePath + "/" + subfolder.getFolderName();
			uploadedFiles.addAll(uploadFolder(createdFolder, subfolder, subfolderRelativePath, progressAware));
		}

		return uploadedFiles;
	}

	private CloudFolder createFolderSafe(CloudFolder parent, String folderName) throws BackendException {
		try {
			return cloudContentRepository.create( //
					cloudContentRepository.folder(parent, folderName));
		} catch (CloudNodeAlreadyExistsException e) {
			return cloudContentRepository.folder(parent, folderName);
		}
	}

	private CloudFile upload(CloudFolder folder, UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
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
