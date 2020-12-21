package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.DownloadFile;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.util.Optional;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DownloadFileTest {

	private CloudContentRepository cloudContentRepository = mock(CloudContentRepository.class);

	private CloudFile downloadFile = mock(CloudFile.class);

	private OutputStream dataSink = mock(OutputStream.class);

	private ProgressAware<DownloadState> progressAware = mock(ProgressAware.class);

	@Test
	public void testDownloadFile() throws BackendException {
		DownloadFiles inTest = testCandidate(singletonList(new DownloadFile.Builder() //
				.setDownloadFile(downloadFile) //
				.setDataSink(dataSink) //
				.build()));

		List<CloudFile> results = inTest.execute(progressAware);

		verify(cloudContentRepository).read(downloadFile, Optional.empty(), dataSink, progressAware);
		verifyNoMoreInteractions(cloudContentRepository);

		assertThat(results, is(singletonList(downloadFile)));
	}

	@Test
	public void testDownloadFiles() throws BackendException {
		DownloadFile file = new DownloadFile.Builder() //
				.setDownloadFile(downloadFile) //
				.setDataSink(dataSink) //
				.build();
		List<DownloadFile> downloadFiles = asList(file, file);
		DownloadFiles inTest = testCandidate(downloadFiles);

		List<CloudFile> results = inTest.execute(progressAware);

		verify(cloudContentRepository, times(downloadFiles.size())) //
				.read(downloadFile, Optional.empty(), dataSink, progressAware);
		verifyNoMoreInteractions(cloudContentRepository);

		assertThat(results, is(asList(downloadFile, downloadFile)));

	}

	private DownloadFiles testCandidate(List<DownloadFile> downloadFiles) {
		return new DownloadFiles(cloudContentRepository, downloadFiles);
	}
}
