package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
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
public class UploadFiles {

	private final Context context;
	private final CloudContentRepository cloudContentRepository;
	private final CloudFolder parent;
	private final List<UploadFile> files;
	private Set<String> completedFiles = Collections.emptySet();
	private FileUploadedCallback fileUploadedCallback = FileUploadedCallback.NO_OP;

	private volatile boolean cancelled;
	private final Flag cancelledFlag = () -> cancelled;

	public UploadFiles(Context context, //
			CloudContentRepository cloudContentRepository, //
			@Parameter CloudFolder parent, //
			@Parameter List<UploadFile> files) {
		this.context = context;
		this.cloudContentRepository = cloudContentRepository;
		this.parent = parent;
		this.files = files;
	}

	public void setCompletedFiles(Set<String> completedFiles) {
		this.completedFiles = completedFiles;
	}

	public void setFileUploadedCallback(FileUploadedCallback fileUploadedCallback) {
		this.fileUploadedCallback = fileUploadedCallback;
	}

	public void onCancel() {
		cancelled = true;
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
		List<CloudFile> uploadedFiles = new ArrayList<>();
		for (UploadFile file : files) {
			if (completedFiles.contains(file.getFileName())) {
				continue;
			}
			CloudFile uploadedFile = upload(file, progressAware);
			uploadedFiles.add(uploadedFile);
			fileUploadedCallback.onFileUploaded(file.getFileName(), uploadedFile);
		}
		return uploadedFiles;
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
			StreamHelper.copy(in, out);
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

}
