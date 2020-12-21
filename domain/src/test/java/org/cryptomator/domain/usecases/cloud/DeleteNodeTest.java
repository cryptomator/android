package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DeleteNodeTest {

	private final CloudContentRepository cloudContentRepository = mock(CloudContentRepository.class);

	public CloudNode cloudFile = Mockito.mock(CloudFile.class);

	public CloudNode cloudFolder = Mockito.mock(CloudFolder.class);

	@Test
	public void testDeleteCloudFile() throws BackendException {
		DeleteNodes inTest = testCandidate(singletonList(cloudFile));

		List<CloudNode> results = inTest.execute();

		verify(cloudContentRepository).delete(cloudFile);
		verifyNoMoreInteractions(cloudContentRepository);
		assertThat(results, is(singletonList(cloudFile)));
	}

	@Test
	public void testDeleteCloudFolder() throws BackendException {
		DeleteNodes inTest = testCandidate(singletonList(cloudFolder));

		List<CloudNode> results = inTest.execute();

		verify(cloudContentRepository).delete(cloudFolder);
		verifyNoMoreInteractions(cloudContentRepository);
		assertThat(results, is(singletonList(cloudFolder)));
	}

	@Test
	public void testDeleteCloudNodes() throws BackendException {
		List<CloudNode> cloudNodes = Arrays.asList(cloudFile, cloudFolder);

		DeleteNodes inTest = testCandidate(cloudNodes);
		List<CloudNode> results = inTest.execute();

		verify(cloudContentRepository).delete(cloudFile);
		verify(cloudContentRepository).delete(cloudFolder);
		verifyNoMoreInteractions(cloudContentRepository);

		assertThat(results, is(cloudNodes));
	}

	private DeleteNodes testCandidate(List<CloudNode> cloudNodes) {
		return new DeleteNodes(cloudContentRepository, cloudNodes);
	}
}
