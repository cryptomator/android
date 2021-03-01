package org.cryptomator.domain.usecases.cloud;

import org.mockito.invocation.InvocationOnMock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class DataSourceCapturingAnswer<T> implements org.mockito.stubbing.Answer<T> {

	private final T result;
	private final int argIndex;
	private ByteArrayOutputStream out;

	DataSourceCapturingAnswer(T result, int argIndex) {
		this.result = result;
		this.argIndex = argIndex;
	}

	@Override
	public T answer(InvocationOnMock invocation) throws Throwable {
		InputStream in = ((DataSource) invocation.getArguments()[argIndex]).open(null);
		out = new ByteArrayOutputStream();
		copy(in, out);
		return result;
	}

	private void copy(InputStream in, ByteArrayOutputStream out) {
		byte[] buffer = new byte[4096];
		int read;
		try {
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] toByteArray() {
		return out.toByteArray();
	}

}
