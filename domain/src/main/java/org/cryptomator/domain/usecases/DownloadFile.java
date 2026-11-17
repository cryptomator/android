package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudFile;

import java.io.IOException;
import java.io.OutputStream;

public class DownloadFile {

	private final CloudFile downloadFile;

	private final DataSink dataSink;

	private DownloadFile(Builder builder) {
		this.downloadFile = builder.downloadFile;
		this.dataSink = builder.dataSink;
	}

	public CloudFile getDownloadFile() {
		return downloadFile;
	}

	public OutputStream openDataSink() throws IOException {
		return dataSink.open();
	}

	/**
	 * Opens where a file is written to. Opening a document of another app, or of the app's own documents provider, can
	 * take long, so it is done on the thread that downloads the file and not where the download is set up.
	 */
	public interface DataSink {

		OutputStream open() throws IOException;
	}

	public static class Builder {

		private CloudFile downloadFile;
		private DataSink dataSink;

		public Builder setDownloadFile(CloudFile downloadFile) {
			this.downloadFile = downloadFile;
			return this;
		}

		public Builder setDataSink(OutputStream dataSink) {
			this.dataSink = () -> dataSink;
			return this;
		}

		public Builder setDataSink(DataSink dataSink) {
			this.dataSink = dataSink;
			return this;
		}

		public DownloadFile build() {
			return new DownloadFile(this);
		}
	}
}
