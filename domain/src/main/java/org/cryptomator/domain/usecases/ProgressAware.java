package org.cryptomator.domain.usecases;

import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.ProgressState;

public interface ProgressAware<T extends ProgressState> {

	ProgressAware NO_OP_PROGRESS_AWARE = progress -> {
	};

	void onProgress(Progress<T> progress);

}
