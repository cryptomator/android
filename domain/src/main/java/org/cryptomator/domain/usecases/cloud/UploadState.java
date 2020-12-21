package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;

public class UploadState implements FileTransferState {

	private final CloudFile file;
	private final boolean upload;

	public static UploadState upload(CloudFile file) {
		return new UploadState(file, true);
	}

	public static UploadState encryption(CloudFile file) {
		return new UploadState(file, false);
	}

	private UploadState(CloudFile file, boolean upload) {
		this.upload = upload;
		this.file = file;
	}

	@Override
	public CloudFile file() {
		return file;
	}

	public boolean isUpload() {
		return upload;
	}

	public boolean isEncryption() {
		return !upload;
	}

	public UploadState withFile(CloudFile file) {
		return new UploadState(file, upload);
	}
}
