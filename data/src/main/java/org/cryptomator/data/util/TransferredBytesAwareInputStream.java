package org.cryptomator.data.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public abstract class TransferredBytesAwareInputStream extends InputStream {

	private static final int EOF = -1;

	private final InputStream in;

	private long transferred;

	public TransferredBytesAwareInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public int read() throws IOException {
		int result = in.read();
		if (result != EOF) {
			bytesTransferred(++transferred);
		}
		return result;
	}

	@Override
	public int read(byte @NotNull [] b) throws IOException {
		int result = in.read(b);
		if (result != EOF) {
			transferred += result;
			bytesTransferred(transferred);
		}
		return result;
	}

	@Override
	public int read(byte @NotNull [] b, int off, int len) throws IOException {
		int result = in.read(b, off, len);
		if (result != EOF) {
			transferred += result;
			bytesTransferred(transferred);
		}
		return result;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	@Override
	public int available() throws IOException {
		return in.available();
	}

	@Override
	public long skip(long n) throws IOException {
		long result = in.skip(n);
		transferred += result;
		bytesTransferred(transferred);
		return result;
	}

	public abstract void bytesTransferred(long transferred);
}
