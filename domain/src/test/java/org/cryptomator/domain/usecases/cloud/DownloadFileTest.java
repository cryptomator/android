package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.DownloadFile;
import org.cryptomator.domain.usecases.ProgressAware;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class DownloadFileTest {

	private CloudContentRepository cloudContentRepository = mock(CloudContentRepository.class);

	private VaultDocumentRepository vaultDocumentRepository = mock(VaultDocumentRepository.class);

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

		verify(cloudContentRepository).read(downloadFile, dataSink, progressAware);
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
				.read(downloadFile, dataSink, progressAware);
		verifyNoMoreInteractions(cloudContentRepository);

		assertThat(results, is(asList(downloadFile, downloadFile)));

	}

	@Test
	public void testTheDataSinkIsOpenedWhenTheFileIsDownloadedNotWhenItIsSetUp() throws BackendException {
		List<String> events = new ArrayList<>();
		DownloadFile file = new DownloadFile.Builder() //
				.setDownloadFile(downloadFile) //
				.setDataSink(() -> {
					events.add("opened");
					return dataSink;
				}) //
				.build();
		DownloadFiles inTest = testCandidate(singletonList(file));
		assertTrue(events.isEmpty());

		inTest.execute(progressAware);

		assertEquals(singletonList("opened"), events);
		verify(cloudContentRepository).read(downloadFile, dataSink, progressAware);
	}

	@Test
	public void testADataSinkThatCannotBeOpenedFailsTheDownload() {
		DownloadFiles inTest = testCandidate(singletonList(new DownloadFile.Builder() //
				.setDownloadFile(downloadFile) //
				.setDataSink(() -> {
					throw new FileNotFoundException("gone");
				}) //
				.build()));

		assertThrows(NoSuchCloudFileException.class, () -> inTest.execute(progressAware));
		verifyNoMoreInteractions(cloudContentRepository);
	}

	@Test
	public void testTheDataSinkIsClosedAfterTheDownload() throws Exception {
		testCandidate(singletonList(new DownloadFile.Builder() //
				.setDownloadFile(downloadFile) //
				.setDataSink(() -> dataSink) //
				.build())).execute(progressAware);

		verify(dataSink).close();
	}

	@Test
	public void testAFailedDownloadFromTheCloudClosesItsDataSinkAndOpensNoFurtherOnes() throws Exception {
		doThrow(new FatalBackendException("failed")).when(cloudContentRepository).read(downloadFile, dataSink, progressAware);

		assertFailedDownloadClosesItsDataSinkAndOpensNoFurtherOnes();
	}

	@Test
	public void testAFailedDownloadFromAVaultClosesItsDataSinkAndOpensNoFurtherOnes() throws Exception {
		VaultPath path = mock(VaultPath.class);
		when(vaultDocumentRepository.pathOf(downloadFile)).thenReturn(path);
		doThrow(new FatalBackendException("failed")).when(vaultDocumentRepository).read(path, dataSink, progressAware);

		assertFailedDownloadClosesItsDataSinkAndOpensNoFurtherOnes();
	}

	private void assertFailedDownloadClosesItsDataSinkAndOpensNoFurtherOnes() throws Exception {
		List<String> laterOpened = new ArrayList<>();
		DownloadFiles inTest = testCandidate(asList( //
				new DownloadFile.Builder().setDownloadFile(downloadFile).setDataSink(() -> dataSink).build(), //
				new DownloadFile.Builder().setDownloadFile(mock(CloudFile.class)).setDataSink(() -> {
					laterOpened.add("opened");
					return mock(OutputStream.class);
				}).build()));

		assertThrows(FatalBackendException.class, () -> inTest.execute(progressAware));

		verify(dataSink).close();
		assertTrue(laterOpened.isEmpty());
	}

	private DownloadFiles testCandidate(List<DownloadFile> downloadFiles) {
		return new DownloadFiles(cloudContentRepository, vaultDocumentRepository, downloadFiles);
	}
}
