package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudNode;

public class ResultRenamed<T extends CloudNode> {

	private final T value;
	private final String oldName;

	public ResultRenamed(T value, String oldName) {
		this.value = value;
		this.oldName = oldName;
	}

	public T value() {
		return value;
	}

	public String getOldName() {
		return oldName;
	}
}