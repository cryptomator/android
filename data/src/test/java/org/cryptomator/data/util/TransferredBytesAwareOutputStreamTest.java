package org.cryptomator.data.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class TransferredBytesAwareOutputStreamTest {

	private static final int AN_INTEGER = 5678;
	private static final byte[] A_BYTE_ARRAY = new byte[10];
	private static final int ANOTHER_INTEGER = 8923;

	private OutputStream delegate;
	private CountingTransferredBytesAwareOutputStream inTest;

	@BeforeEach
	public void setup() {
		delegate = Mockito.mock(OutputStream.class);
		inTest = new CountingTransferredBytesAwareOutputStream(delegate);
	}

	@Test
	public void testNewInstanceDidNotTransferAnything() {
		assertThat(inTest.getTransferred(), is(0L));
	}

	@Test
	public void testIntegerWriteDelegatesToWrite() throws IOException {
		inTest.write(AN_INTEGER);

		verify(delegate).write(AN_INTEGER);
	}

	@Test
	public void testIntegerWriteTransfersOneByte() throws IOException {
		inTest.write(AN_INTEGER);

		assertThat(inTest.getTransferred(), is(1L));
	}

	@Test
	public void testIntegerWriteWithExceptionDoesNotTransferAnything() throws IOException {
		doThrow(new IOException()).when(delegate).write(AN_INTEGER);

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.write(AN_INTEGER);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testArrayWriteDelegatesToWrite() throws IOException {
		inTest.write(A_BYTE_ARRAY);

		verify(delegate).write(A_BYTE_ARRAY);
	}

	@Test
	public void testArrayWriteTransfersBytes() throws IOException {
		inTest.write(A_BYTE_ARRAY);

		assertThat(inTest.getTransferred(), is((long) A_BYTE_ARRAY.length));
	}

	@Test
	public void testArrayWriteWithExceptionDoesNotTransferAnything() throws IOException {
		doThrow(new IOException()).when(delegate).write(A_BYTE_ARRAY);

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.write(A_BYTE_ARRAY);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testArrayWithRangeWriteDelegatesToWrite() throws IOException {
		inTest.write(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		verify(delegate).write(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);
	}

	@Test
	public void testArrayWithRangeWriteTransfersBytes() throws IOException {
		inTest.write(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		assertThat(inTest.getTransferred(), is((long) ANOTHER_INTEGER));
	}

	@Test
	public void testArrayWithRangeWriteWithExceptionDoesNotTransferAnything() throws IOException {
		doThrow(new IOException()).when(delegate).write(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.write(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testCloseDelegatesToClose() throws IOException {
		inTest.close();

		verify(delegate).close();
	}

	@Test
	public void testFlushDelegatesToFlush() throws IOException {
		inTest.flush();

		verify(delegate).flush();
	}

	private static class CountingTransferredBytesAwareOutputStream extends TransferredBytesAwareOutputStream {

		private long transferred;

		public CountingTransferredBytesAwareOutputStream(OutputStream delegate) {
			super(delegate);
		}

		@Override
		public void bytesTransferred(long transferred) {
			this.transferred = transferred;
		}

		public long getTransferred() {
			return transferred;
		}

	}

}
