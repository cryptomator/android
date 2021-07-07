package org.cryptomator.domain.usecases;

import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.ProgressState;
import org.cryptomator.domain.usecases.cloud.UploadState;

public interface ProgressAware<T extends ProgressState> {

	ProgressAware<DownloadState> NO_OP_PROGRESS_AWARE_DOWNLOAD = progress -> {
	};

	ProgressAware<UploadState> NO_OP_PROGRESS_AWARE_UPLOAD = progress -> {
	};

	void onProgress(Progress<T> progress);

}
