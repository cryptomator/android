package org.cryptomator.domain.exception;

public class EmptyDirFileException extends BackendException {

	private final String filePath;
	private final String dirName;

	public EmptyDirFileException(String dirName, String filePath) {
		super(String.format("Empty dir file detected: %s", filePath));
		this.dirName = dirName;
		this.filePath = filePath;
	}

	public String getFilePath() {
		return filePath;
	}

	public String getDirName() {
		return dirName;
	}
}
