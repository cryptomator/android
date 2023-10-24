package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class MoveFileTest {

	private CloudContentRepository cloudContentRepository;

	private CloudFolder parent;

	private CloudFile sourceFile;

	private CloudFile targetFile;

	private CloudFile resultFile;

	@BeforeEach
	public void setup() {
		cloudContentRepository = Mockito.mock(CloudContentRepository.class);
		parent = Mockito.mock(CloudFolder.class);
		sourceFile = Mockito.mock(CloudFile.class);
		targetFile = Mockito.mock(CloudFile.class);
		resultFile = Mockito.mock(CloudFile.class);
	}

	@Test
	public void testMoveFile() throws BackendException {
		MoveFiles inTest = testCandidate(singletonList(sourceFile));
		when(cloudContentRepository.file(parent, null)).thenReturn(targetFile);
		when(cloudContentRepository.move(sourceFile, targetFile)).thenReturn(resultFile);

		List<CloudFile> result = inTest.execute();

		verify(cloudContentRepository).file(parent, null);
		verify(cloudContentRepository).move(sourceFile, targetFile);
		verifyNoMoreInteractions(cloudContentRepository);
		assertThat(result, is(singletonList(resultFile)));
	}

	@Test
	public void testMoveFiles() throws BackendException {
		List<CloudFile> sourceFiles = asList(sourceFile, sourceFile);

		MoveFiles inTest = testCandidate(sourceFiles);
		when(cloudContentRepository.file(parent, null)).thenReturn(targetFile);
		when(cloudContentRepository.move(sourceFile, targetFile)).thenReturn(resultFile);

		List<CloudFile> result = inTest.execute();

		verify(cloudContentRepository, times(sourceFiles.size())).file(parent, null);
		verify(cloudContentRepository, times(sourceFiles.size())).move(sourceFile, targetFile);
		verifyNoMoreInteractions(cloudContentRepository);
		assertThat(result, is(asList(resultFile, resultFile)));
	}

	private MoveFiles testCandidate(List<CloudFile> sourceFile) {
		return new MoveFiles(cloudContentRepository, //
				sourceFile, //
				parent);
	}
}
