package org.cryptomator.domain.usecases.cloud;

import android.content.Context;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static java.util.Arrays.fill;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UploadFileTest {

	private final Context context = mock(Context.class);
	private final CloudContentRepository cloudContentRepository = mock(CloudContentRepository.class);
	private final CloudFolder parent = mock(CloudFolder.class);
	private final CloudFile targetFile = mock(CloudFile.class);
	private final CloudFile resultFile = mock(CloudFile.class);
	private final String fileName = "fileName";
	private final ProgressAware<UploadState> progressAware = mock(ProgressAware.class);

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testInvocationWithFileSizeDelegatesToCloudContentRepository(Boolean replacing) throws BackendException {
		long fileSize = 1337;
		DataSource dataSource = dataSourceWithBytes(0, fileSize, Optional.of(fileSize));
		UploadFiles inTest = testCandidate(dataSource, replacing);
		when(cloudContentRepository.file(parent, fileName, Optional.of(fileSize))).thenReturn(targetFile);
		when(cloudContentRepository.write(same(targetFile), any(DataSource.class), same(progressAware), eq(replacing), eq(fileSize))).thenReturn(resultFile);

		List<CloudFile> result = inTest.execute(progressAware);

		assertThat(result.size(), is(1));
		assertThat(result.get(0), is(resultFile));
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void testInvocationWithoutFileSizeDelegatesToCloudContentRepository(Boolean replacing) throws BackendException, IOException {
		long fileSize = 8893;
		try (DataSource dataSource = dataSourceWithBytes(85, fileSize, Optional.empty())) {
			UploadFiles inTest = testCandidate(dataSource, replacing);
			when(cloudContentRepository.file(parent, fileName, Optional.of(fileSize))).thenReturn(targetFile);
			DataSourceCapturingAnswer capturedStreamData = new DataSourceCapturingAnswer(resultFile, 1);
			when(cloudContentRepository.write(same(targetFile), any(DataSource.class), same(progressAware), eq(replacing), eq(fileSize))).thenAnswer(capturedStreamData);

			List<CloudFile> result = inTest.execute(progressAware);

			assertThat(result.size(), is(1));
			assertThat(result.get(0), is(resultFile));
			assertThat(capturedStreamData.toByteArray(), is(bytes(85, fileSize)));
		}
	}

	private DataSource dataSourceWithBytes(int value, long amount, final Optional<Long> size) {
		if (amount > Integer.MAX_VALUE) {
			throw new IllegalStateException("Can not use values > Integer.MAX_VALUE");
		}
		final byte[] bytes = bytes(value, (int) amount);
		return new DataSource() {

			@Override
			public Optional<Long> size(Context context) {
				return size;
			}

			@Override
			public InputStream open(Context context) throws IOException {
				return new ByteArrayInputStream(bytes);
			}

			@Override
			public DataSource decorate(DataSource delegate) {
				return delegate;
			}

			@Override
			public void close() throws IOException {
				// do nothing
			}
		};
	}

	private byte[] bytes(int value, long amount) {
		if (amount > Integer.MAX_VALUE) {
			throw new IllegalStateException("Can not use values > Integer.MAX_VALUE");
		}
		byte[] data = new byte[(int) amount];
		fill(data, (byte) value);
		return data;
	}

	private UploadFiles testCandidate(DataSource dataSource, Boolean replacing) {
		return new UploadFiles( //
				context, //
				cloudContentRepository, //
				parent, //
				singletonList(new UploadFile.Builder() //
						.withFileName(fileName) //
						.withDataSource(dataSource) //
						.thatIsReplacing(replacing) //
						.build()));
	}

}
