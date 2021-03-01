package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.util.Optional;

import java.io.IOException;
import java.io.InputStream;

public class CancelAwareDataSource implements DataSource {

	private final DataSource delegate;
	private final Flag cancelled;

	private CancelAwareDataSource(DataSource delegate, Flag cancelled) {
		this.delegate = delegate;
		this.cancelled = cancelled;
	}

	public static CancelAwareDataSource wrap(DataSource delegate, Flag cancelled) {
		return new CancelAwareDataSource(delegate, cancelled);
	}

	@Override
	public Optional<Long> size(Context context) {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return delegate.size(context);
	}

	@Override
	public InputStream open(Context context) throws IOException {
		if (cancelled.get()) {
			throw new CancellationException();
		}
		return CancelAwareInputStream.wrap(delegate.open(context), cancelled);
	}

	public CancelAwareDataSource decorate(DataSource delegate) {
		return new CancelAwareDataSource(delegate, cancelled);
	}

	@Override
	public void close() throws IOException {
		delegate.close();
	}
}
