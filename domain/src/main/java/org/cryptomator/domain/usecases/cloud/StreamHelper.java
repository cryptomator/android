package org.cryptomator.domain.usecases.cloud;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class StreamHelper {

	private static final int EOF = -1;
	private static final int BUFFER_SIZE = 4096;

	private StreamHelper() {
	}

	static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		try {
			int read;
			while ((read = in.read(buffer)) != EOF) {
				out.write(buffer, 0, read);
			}
		} finally {
			closeQuietly(in);
			closeQuietly(out);
		}
	}

	static void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}
}
