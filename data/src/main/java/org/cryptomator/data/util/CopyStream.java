package org.cryptomator.data.util;

import org.cryptomator.domain.exception.FatalBackendException;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CopyStream {

	private static final int DEFAULT_COPY_BUFFER_SIZE = 16 << 10; // 16 KiB

	public static void copyStreamToStream(InputStream in, OutputStream out) {
		copyStreamToStream(in, out, new byte[DEFAULT_COPY_BUFFER_SIZE]);
	}

	private static void copyStreamToStream(InputStream in, OutputStream out, byte[] copyBuffer) {
		while (true) {
			int count;
			try {
				count = in.read(copyBuffer);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}

			if (count == -1)
				break;

			try {
				out.write(copyBuffer, 0, count);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}
	}

	public static void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (RuntimeException rethrown) {
				throw rethrown;
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public static byte[] toByteArray(InputStream inputStream) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int read;
		byte[] data = new byte[1024];
		try {
			while ((read = inputStream.read(data, 0, data.length)) != -1) {
				buffer.write(data, 0, read);
			}
			buffer.flush();
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
		return buffer.toByteArray();
	}
}
