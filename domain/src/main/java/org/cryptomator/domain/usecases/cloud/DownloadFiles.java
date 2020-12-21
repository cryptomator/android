package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.DownloadFile;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@UseCase
class DownloadFiles {

	private final CloudContentRepository cloudContentRepository;
	private final List<DownloadFile> downloadFiles;

	public DownloadFiles(CloudContentRepository cloudContentRepository, //
			@Parameter List<DownloadFile> downloadFiles) {
		this.cloudContentRepository = cloudContentRepository;
		this.downloadFiles = downloadFiles;
	}

	public List<CloudFile> execute(ProgressAware<DownloadState> progressAware) throws BackendException {
		List<CloudFile> downloadedFiles = new ArrayList<>();
		for (DownloadFile file : downloadFiles) {
			try {
				cloudContentRepository.read(file.getDownloadFile(), Optional.empty(), file.getDataSink(), progressAware);
				downloadedFiles.add(file.getDownloadFile());
			} finally {
				closeQuietly(file.getDataSink());
			}
		}
		return downloadedFiles;
	}

	private void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// ignore
		}
	}

}
