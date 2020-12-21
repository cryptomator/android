package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;

public class DownloadState implements FileTransferState {

	private final CloudFile file;
	private final boolean download;

	public static DownloadState download(CloudFile file) {
		return new DownloadState(file, true);
	}

	public static DownloadState decryption(CloudFile file) {
		return new DownloadState(file, false);
	}

	private DownloadState(CloudFile file, boolean download) {
		this.download = download;
		this.file = file;
	}

	@Override
	public CloudFile file() {
		return file;
	}

	public boolean isDownload() {
		return download;
	}

	public DownloadState withFile(CloudFile file) {
		return new DownloadState(file, download);
	}

}
