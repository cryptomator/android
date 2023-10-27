package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class MoveFolderTest {

	private CloudContentRepository cloudContentRepository;

	private CloudFolder parent;

	private CloudFolder sourceFolder;

	private CloudFolder targetFolder;

	private CloudFolder resultFolder;

	@BeforeEach
	public void setup() {
		cloudContentRepository = Mockito.mock(CloudContentRepository.class);
		parent = Mockito.mock(CloudFolder.class);
		sourceFolder = Mockito.mock(CloudFolder.class);
		targetFolder = Mockito.mock(CloudFolder.class);
		resultFolder = Mockito.mock(CloudFolder.class);
	}

	@Test
	public void testMoveFolder() throws BackendException {
		MoveFolders inTest = testCandidate(singletonList(sourceFolder));
		when(cloudContentRepository.folder(parent, null)).thenReturn(targetFolder);
		when(cloudContentRepository.move(sourceFolder, targetFolder)).thenReturn(resultFolder);

		List<CloudFolder> result = inTest.execute();

		verify(cloudContentRepository).folder(parent, null);
		verify(cloudContentRepository).move(sourceFolder, targetFolder);
		verifyNoMoreInteractions(cloudContentRepository);
		MatcherAssert.assertThat(result, is(singletonList(resultFolder)));
	}

	@Test
	public void testMoveFolders() throws BackendException {
		List<CloudFolder> sourceFiles = asList(sourceFolder, sourceFolder);

		MoveFolders inTest = testCandidate(sourceFiles);
		when(cloudContentRepository.folder(parent, null)).thenReturn(targetFolder);
		when(cloudContentRepository.move(sourceFolder, targetFolder)).thenReturn(resultFolder);

		List<CloudFolder> result = inTest.execute();

		verify(cloudContentRepository, times(sourceFiles.size())).folder(parent, null);
		verify(cloudContentRepository, times(sourceFiles.size())).move(sourceFolder, targetFolder);
		verifyNoMoreInteractions(cloudContentRepository);
		MatcherAssert.assertThat(result, is(asList(resultFolder, resultFolder)));
	}

	private MoveFolders testCandidate(List<CloudFolder> sourceFolder) {
		return new MoveFolders(cloudContentRepository, //
				sourceFolder, //
				parent);
	}
}
