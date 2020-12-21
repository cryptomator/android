package org.cryptomator.data.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransferredBytesAwareInputStreamTest {

	private static final int AN_INTEGER = 5837;
	private static final int EOF = -1;
	private static final byte[] A_BYTE_ARRAY = new byte[10];
	private static final int ANOTHER_INTEGER = 7820;
	private static final int A_THIRD_INTEGER = 678923;
	private static final long A_LONG = 67890L;
	private static final long ANOTHER_LONG = 890123L;

	private InputStream delegate;

	private CountingTransferredBytesAwareInputStream inTest;

	@BeforeEach
	public void setup() {
		delegate = Mockito.mock(InputStream.class);
		inTest = new CountingTransferredBytesAwareInputStream(delegate);
	}

	@Test
	public void testNewInstanceDidNotTransferAnything() throws IOException {
		assertThat(inTest.getTransferred(), is(0L));
	}

	@Test
	public void testReadDelegatesToRead() throws IOException {
		when(delegate.read()).thenReturn(AN_INTEGER);

		int result = inTest.read();

		assertThat(result, is(AN_INTEGER));
	}

	@Test
	public void testReadTransfersOneByte() throws IOException {
		when(delegate.read()).thenReturn(AN_INTEGER);

		inTest.read();

		assertThat(inTest.getTransferred(), is(1L));
	}

	@Test
	public void testReadWithExceptionDoesNotTransferAnything() throws IOException {
		when(delegate.read()).thenThrow(new IOException());

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.read();
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testReadWithEofDoesNotTransferAnything() throws IOException {
		when(delegate.read()).thenReturn(EOF);

		int result = inTest.read();

		assertThat(result, is(EOF));
		assertThat(inTest.getTransferred(), is(0L));
	}

	@Test
	public void testArrayReadDelegatesToRead() throws IOException {
		when(delegate.read(A_BYTE_ARRAY)).thenReturn(AN_INTEGER);

		int result = inTest.read(A_BYTE_ARRAY);

		assertThat(result, is(AN_INTEGER));
	}

	@Test
	public void testArrayReadTransfersBytes() throws IOException {
		when(delegate.read(A_BYTE_ARRAY)).thenReturn(AN_INTEGER);

		inTest.read(A_BYTE_ARRAY);

		assertThat(inTest.getTransferred(), is((long) AN_INTEGER));
	}

	@Test
	public void testArrayReadWithExceptionDoesNotTransferAnything() throws IOException {
		when(delegate.read(A_BYTE_ARRAY)).thenThrow(new IOException());

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.read(A_BYTE_ARRAY);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testArrayReadWithEofDoesNotTransferAnything() throws IOException {
		when(delegate.read(A_BYTE_ARRAY)).thenReturn(EOF);

		inTest.read(A_BYTE_ARRAY);

		assertThat(inTest.getTransferred(), is(0L));
	}

	@Test
	public void testArrayWithRangeReadDelegatesToRead() throws IOException {
		when(delegate.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER)).thenReturn(A_THIRD_INTEGER);

		int result = inTest.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		assertThat(result, is(A_THIRD_INTEGER));
	}

	@Test
	public void testArrayWithRangeReadTransfersBytes() throws IOException {
		when(delegate.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER)).thenReturn(A_THIRD_INTEGER);

		inTest.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		assertThat(inTest.getTransferred(), is((long) A_THIRD_INTEGER));
	}

	@Test
	public void testArrayWithRangeReadWithExceptionDoesNotTransferAnything() throws IOException {
		when(delegate.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER)).thenThrow(new IOException());

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	@Test
	public void testArrayWithRangeReadWithEofDoesNotTransferAnything() throws IOException {
		when(delegate.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER)).thenReturn(EOF);

		inTest.read(A_BYTE_ARRAY, AN_INTEGER, ANOTHER_INTEGER);

		assertThat(inTest.getTransferred(), is(0L));
	}

	@Test
	public void testCloseDelegatesToClose() throws IOException {
		inTest.close();

		verify(delegate).close();
	}

	@Test
	public void testAvailableDelegatesToAvailable() throws IOException {
		when(delegate.available()).thenReturn(AN_INTEGER);

		int result = inTest.available();

		assertThat(result, is(AN_INTEGER));
	}

	@Test
	public void testResetThrowsIOException() {
		Assertions.assertThrows(IOException.class, () -> inTest.reset());
	}

	@Test
	public void testMarkSupportedReturnsFalse() {
		boolean result = inTest.markSupported();

		assertFalse(result);
	}

	@Test
	public void testSkipDelegatesToSkip() throws IOException {
		when(delegate.skip(A_LONG)).thenReturn(ANOTHER_LONG);

		long result = inTest.skip(A_LONG);

		assertThat(result, is(ANOTHER_LONG));
	}

	@Test
	public void testSkipTransfersBytes() throws IOException {
		when(delegate.skip(A_LONG)).thenReturn(ANOTHER_LONG);

		inTest.skip(A_LONG);

		assertThat(inTest.getTransferred(), is(ANOTHER_LONG));
	}

	@Test
	public void testSkipWithExceptionDoesNotTransferAnything() throws IOException {
		when(delegate.skip(A_LONG)).thenThrow(new IOException());

		Assertions.assertThrows(IOException.class, () -> {
			try {
				inTest.skip(A_LONG);
			} finally {
				assertThat(inTest.getTransferred(), is(0L));
			}
		});
	}

	private static class CountingTransferredBytesAwareInputStream extends TransferredBytesAwareInputStream {

		private long transferred;

		public CountingTransferredBytesAwareInputStream(InputStream delegate) {
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
