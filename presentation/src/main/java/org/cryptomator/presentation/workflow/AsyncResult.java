package org.cryptomator.presentation.workflow;

import org.cryptomator.generator.BoundCallback;

public abstract class AsyncResult {

	private final BoundCallback callback;

	AsyncResult(BoundCallback callback) {
		this.callback = callback;
	}

	public BoundCallback callback() {
		return callback;
	}

}
