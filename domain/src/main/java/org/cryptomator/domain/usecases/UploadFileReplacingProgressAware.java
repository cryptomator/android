package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;

public class UploadFileReplacingProgressAware implements ProgressAware<UploadState> {

	private final CloudFile file;
	private final ProgressAware<UploadState> delegate;

	public UploadFileReplacingProgressAware(CloudFile file, ProgressAware<UploadState> delegate) {
		this.file = file;
		this.delegate = delegate;
	}

	@Override
	public void onProgress(Progress<UploadState> progress) {
		if (progress.state() == null) {
			delegate.onProgress(progress);
		} else {
			delegate.onProgress(progress.withState(progress.state().withFile(file)));
		}
	}
}
