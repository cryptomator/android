package org.cryptomator.domain.usecases;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@UseCase
class CopyData {

	private static final int EOF = -1;
	private final InputStream source;
	private final OutputStream target;

	public CopyData(@Parameter InputStream source, @Parameter OutputStream target) {
		this.source = source;
		this.target = target;
	}

	public void execute() throws BackendException {
		try {
			byte[] buffer = new byte[4096];
			int read = 0;
			while (read != EOF) {
				read = source.read(buffer);
				if (read > 0) {
					target.write(buffer, 0, read);
				}
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

}
