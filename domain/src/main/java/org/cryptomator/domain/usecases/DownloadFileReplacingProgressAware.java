package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;

public class DownloadFileReplacingProgressAware implements ProgressAware<DownloadState> {

	private final CloudFile file;
	private final ProgressAware<DownloadState> delegate;

	public DownloadFileReplacingProgressAware(CloudFile file, ProgressAware<DownloadState> delegate) {
		this.file = file;
		this.delegate = delegate;
	}

	@Override
	public void onProgress(Progress<DownloadState> progress) {
		if (progress.state() == null) {
			delegate.onProgress(progress);
		} else {
			delegate.onProgress(progress.withState(progress.state().withFile(file)));
		}
	}
}
