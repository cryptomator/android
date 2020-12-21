package org.cryptomator.presentation.workflow;

import org.cryptomator.generator.BoundCallback;

import java.io.Serializable;

class SerializableResult<T extends Serializable> extends AsyncResult {

	private final T result;

	SerializableResult(BoundCallback callback, T result) {
		super(callback);
		this.result = result;
	}

	public T getResult() {
		return result;
	}

}
