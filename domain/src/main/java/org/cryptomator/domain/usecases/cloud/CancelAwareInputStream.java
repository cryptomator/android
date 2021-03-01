package org.cryptomator.domain.usecases.cloud;

import androidx.annotation.NonNull;

import org.cryptomator.domain.exception.CancellationException;

import java.io.IOException;
import java.io.InputStream;

class CancelAwareInputStream extends InputStream {

	private final InputStream delegate;
	private final Flag cancelled;

	private CancelAwareInputStream(InputStream delegate, Flag cancelled) {
		this.delegate = delegate;
		this.cancelled = cancelled;
	}

	public static CancelAwareInputStream wrap(InputStream delegate, Flag cancelled) {
		return new CancelAwareInputStream(delegate, cancelled);
	}

	@Override
	public int read() throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.read();
	}

	@Override
	public long skip(long n) throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.skip(n);
	}

	@Override
	public int available() throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.available();
	}

	@Override
	public int read(@NonNull byte[] b) throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.read(b);
	}

	@Override
	public int read(@NonNull byte[] b, int off, int len) throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.read(b, off, len);
	}

	@Override
	public void close() throws IOException {
		delegate.close();
		if (cancelled.get()) {
			throw new CancellationException();
		}
	}

	@Override
	public synchronized void reset() throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		delegate.reset();
	}

	@Override
	public synchronized void mark(int readlimit) {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		delegate.mark(readlimit);
	}

	@Override
	public boolean markSupported() {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.markSupported();
	}
}
