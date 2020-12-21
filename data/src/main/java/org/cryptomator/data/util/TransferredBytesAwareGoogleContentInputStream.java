package org.cryptomator.data.util;

import com.google.api.client.http.AbstractInputStreamContent;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public abstract class TransferredBytesAwareGoogleContentInputStream extends AbstractInputStreamContent implements Closeable {

	private final InputStream data;
	private final long size;

	/**
	 * @param size the size of the data to upload or less than zero if not known
	 */
	public TransferredBytesAwareGoogleContentInputStream(String type, InputStream data, long size) {
		super(type);
		this.data = new TransferredBytesAwareInputStream(data) {
			@Override
			public void bytesTransferred(long transferred) {
				TransferredBytesAwareGoogleContentInputStream.this.bytesTransferred(transferred);
			}
		};
		this.size = size;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return data;
	}

	@Override
	public long getLength() throws IOException {
		return size;
	}

	@Override
	public boolean retrySupported() {
		return false;
	}

	@Override
	public void close() throws IOException {
		data.close();
	}

	public abstract void bytesTransferred(long transferred);
}
