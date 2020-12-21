package org.cryptomator.util.crypto;

import java.security.UnrecoverableKeyException;

public class UnrecoverableStorageKeyException extends IllegalStateException {

	public UnrecoverableStorageKeyException(UnrecoverableKeyException e) {
		super(e);
	}

}
