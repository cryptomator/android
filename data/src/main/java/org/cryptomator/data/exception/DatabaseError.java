package org.cryptomator.data.exception;

public enum DatabaseError {
	RENAME_VAULT("Cannot rename vault."), DELETE_VAULT("Cannot delete vault.");

	private final String errorMessage;

	DatabaseError(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
