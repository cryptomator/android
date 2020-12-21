package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudFile;

import java.io.OutputStream;

public class DownloadFile {

	private final CloudFile downloadFile;

	private final OutputStream dataSink;

	private DownloadFile(Builder builder) {
		this.downloadFile = builder.downloadFile;
		this.dataSink = builder.dataSink;
	}

	public CloudFile getDownloadFile() {
		return downloadFile;
	}

	public OutputStream getDataSink() {
		return dataSink;
	}

	public static class Builder {

		private CloudFile downloadFile;
		private OutputStream dataSink;

		public Builder setDownloadFile(CloudFile downloadFile) {
			this.downloadFile = downloadFile;
			return this;
		}

		public Builder setDataSink(OutputStream dataSink) {
			this.dataSink = dataSink;
			return this;
		}

		public DownloadFile build() {
			return new DownloadFile(this);
		}
	}
}
