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
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@UseCase
class DownloadFiles {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<DownloadFile> downloadFiles;

	public DownloadFiles(CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			@Parameter List<DownloadFile> downloadFiles) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.downloadFiles = downloadFiles;
	}

	public List<CloudFile> execute(ProgressAware<DownloadState> progressAware) throws BackendException {
		List<CloudFile> downloadedFiles = new ArrayList<>();
		for (DownloadFile file : downloadFiles) {
			OutputStream dataSink = openDataSink(file);
			try {
				VaultPath vaultFile = vaultDocumentRepository.pathOf(file.getDownloadFile());
				if (vaultFile != null) {
					vaultDocumentRepository.read(vaultFile, dataSink, progressAware);
				} else {
					cloudContentRepository.read(file.getDownloadFile(), dataSink, progressAware);
				}
				downloadedFiles.add(file.getDownloadFile());
			} finally {
				closeQuietly(dataSink);
			}
		}
		return downloadedFiles;
	}

	private OutputStream openDataSink(DownloadFile file) throws NoSuchCloudFileException {
		try {
			OutputStream dataSink = file.openDataSink();
			if (dataSink == null) {
				throw new FatalBackendException("No stream to write " + file.getDownloadFile().getName() + " to");
			}
			return dataSink;
		} catch (FileNotFoundException e) {
			throw new NoSuchCloudFileException(file.getDownloadFile().getName());
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private void closeQuietly(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// ignore
		}
	}

}
