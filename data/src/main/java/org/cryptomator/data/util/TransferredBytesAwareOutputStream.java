package org.cryptomator.data.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public abstract class TransferredBytesAwareOutputStream extends OutputStream {

	private final OutputStream out;

	private long transferred;

	public TransferredBytesAwareOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void write(byte @NotNull [] b) throws IOException {
		out.write(b);
		transferred += b.length;
		bytesTransferred(transferred);
	}

	@Override
	public void write(byte @NotNull [] b, int off, int len) throws IOException {
		out.write(b, off, len);
		transferred += len;
		bytesTransferred(transferred);
	}

	@Override
	public void write(int i) throws IOException {
		out.write(i);
		bytesTransferred(++transferred);
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	public abstract void bytesTransferred(long transferred);

}
