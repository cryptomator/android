package org.cryptomator.domain.exception;

public class NoDirFileException extends BackendException {

	private final String cryptoFolderName;
	private final String cloudFolderPath;

	public NoDirFileException(String name, String path) {
		this.cryptoFolderName = name;
		this.cloudFolderPath = path;
	}

	public String getCryptoFolderName() {
		return cryptoFolderName;
	}

	public String getCloudFolderPath() {
		return cloudFolderPath;
	}
}
