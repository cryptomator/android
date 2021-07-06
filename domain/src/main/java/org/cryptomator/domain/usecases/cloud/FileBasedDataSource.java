package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.util.Optional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class FileBasedDataSource implements DataSource {

	private final File file;

	private FileBasedDataSource(File file) {
		this.file = file;
	}

	public static FileBasedDataSource from(File file) {
		return new FileBasedDataSource(file);
	}

	@Override
	public Optional<Long> size(Context context) {
		return Optional.of(file.length());
	}

	@Override
	public Optional<Date> modifiedDate(Context context) {
		return Optional.of(new Date(file.lastModified()));
	}

	@Override
	public InputStream open(Context context) throws IOException {
		return new FileInputStream(file);
	}

	@Override
	public DataSource decorate(DataSource delegate) {
		return delegate;
	}

	@Override
	public void close() throws IOException {
		// Do nothing
	}
}
