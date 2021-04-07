package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.DecryptingReadableByteChannel;
import org.cryptomator.cryptolib.EncryptingWritableByteChannel;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.EmptyDirFileException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoDirFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.DownloadFileReplacingProgressAware;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.UploadFileReplacingProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.cryptomator.data.cloud.crypto.CryptoConstants.DATA_DIR_NAME;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;

abstract class CryptoImplDecorator {

	final CloudContentRepository cloudContentRepository;
	final Context context;
	final DirIdCache dirIdCache;
	final int maxFileNameLength;

	private final Supplier<Cryptor> cryptor;
	private final CloudFolder storageLocation;

	private RootCryptoFolder root;

	CryptoImplDecorator(Context context, Supplier<Cryptor> cryptor, CloudContentRepository cloudContentRepository, CloudFolder storageLocation, DirIdCache dirIdCache, int maxFileNameLength) {
		this.context = context;
		this.cryptor = cryptor;
		this.cloudContentRepository = cloudContentRepository;
		this.storageLocation = storageLocation;
		this.dirIdCache = dirIdCache;
		this.maxFileNameLength = maxFileNameLength;
	}

	abstract CryptoFolder folder(CryptoFolder cryptoParent, String cleartextName) throws BackendException;

	abstract String decryptName(String dirId, String encryptedName);

	abstract String encryptName(CryptoFolder cryptoParent, String name) throws BackendException;

	abstract Optional<String> extractEncryptedName(String ciphertextName);

	abstract List<CryptoNode> list(CryptoFolder cryptoFolder) throws BackendException;

	abstract String encryptFolderName(CryptoFolder cryptoFolder, String name) throws BackendException;

	abstract CryptoSymlink symlink(CryptoFolder cryptoParent, String cleartextName, String target) throws BackendException;

	abstract CryptoFolder create(CryptoFolder folder) throws BackendException;

	abstract CryptoFolder move(CryptoFolder source, CryptoFolder target) throws BackendException;

	abstract CryptoFile move(CryptoFile source, CryptoFile target) throws BackendException;

	abstract void delete(CloudNode node) throws BackendException;

	abstract CryptoFile write(CryptoFile cryptoFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException;

	abstract String loadDirId(CryptoFolder folder) throws BackendException, EmptyDirFileException;

	abstract DirIdInfo createDirIdInfo(CryptoFolder folder) throws BackendException;

	private String dirHash(String directoryId) {
		return cryptor().fileNameCryptor().hashDirectoryId(directoryId);
	}

	private CloudFolder dataFolder() throws BackendException {
		return cloudContentRepository.folder(storageLocation, DATA_DIR_NAME);
	}

	String path(CloudFolder base, String name) {
		return base.getPath() + "/" + name;
	}

	File getInternalCache() {
		return context.getCacheDir();
	}

	List<CryptoFolder> deepCollectSubfolders(CryptoFolder source) throws BackendException {
		Queue<CryptoFolder> queue = new LinkedList<>();
		queue.add(source);

		List<CryptoFolder> result = new LinkedList<>();
		while (!queue.isEmpty()) {
			CryptoFolder folder = queue.remove();
			List<CryptoFolder> subfolders = shallowCollectSubfolders(folder);
			queue.addAll(subfolders);
			result.addAll(subfolders);
		}

		Collections.reverse(result);

		return result;
	}

	private List<CryptoFolder> shallowCollectSubfolders(CryptoFolder source) throws BackendException {
		List<CryptoFolder> result = new LinkedList<>();

		try {
			List<CryptoNode> list = list(source);
			for (CloudNode node : list) {
				if (node instanceof CryptoFolder) {
					result.add((CryptoFolder) node);
				}
			}
		} catch (NoDirFileException e) {
			// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
		}

		return result;
	}

	public RootCryptoFolder root(CryptoCloud cryptoCloud) throws BackendException {
		if (root == null) {
			root = new RootCryptoFolder(cryptoCloud);
		}
		return root;
	}

	public CryptoFolder resolve(CryptoCloud cloud, String path) throws BackendException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		CryptoFolder folder = root(cloud);
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public CryptoFile file(CryptoFolder cryptoParent, String cleartextName) throws BackendException {
		return file(cryptoParent, cleartextName, Optional.empty());
	}

	public CryptoFile file(CryptoFolder cryptoParent, String cleartextName, Optional<Long> cleartextSize) throws BackendException {
		String ciphertextName = encryptFileName(cryptoParent, cleartextName);
		return file(cryptoParent, cleartextName, ciphertextName, cleartextSize);
	}

	private CryptoFile file(CryptoFolder cryptoParent, String cleartextName, String ciphertextName, Optional<Long> cleartextSize) throws BackendException {
		Optional<Long> ciphertextSize;
		if (cleartextSize.isPresent()) {
			ciphertextSize = Optional.of(Cryptors.ciphertextSize(cleartextSize.get(), cryptor()) + cryptor().fileHeaderCryptor().headerSize());
		} else {
			ciphertextSize = Optional.empty();
		}
		CloudFile cloudFile = cloudContentRepository.file(dirIdInfo(cryptoParent).getCloudFolder(), ciphertextName, ciphertextSize);
		return file(cryptoParent, cleartextName, cloudFile, cleartextSize);
	}

	CryptoFile file(CryptoFile cryptoFile, CloudFile cloudFile, Optional<Long> cleartextSize) throws BackendException {
		return file(cryptoFile.getParent(), cryptoFile.getName(), cloudFile, cleartextSize);
	}

	CryptoFile file(CryptoFolder cryptoParent, String cleartextName, CloudFile cloudFile, Optional<Long> cleartextSize) throws BackendException {
		return new CryptoFile(cryptoParent, cleartextName, path(cryptoParent, cleartextName), cleartextSize, cloudFile);
	}

	private String encryptFileName(CryptoFolder cryptoParent, String name) throws BackendException {
		return encryptName(cryptoParent, name);
	}

	CryptoFolder folder(CryptoFolder cryptoParent, String cleartextName, CloudFile dirFile) throws BackendException {
		return new CryptoFolder(cryptoParent, cleartextName, path(cryptoParent, cleartextName), dirFile);
	}

	CryptoFolder folder(CryptoFolder cryptoFolder, CloudFile dirFile) throws BackendException {
		return new CryptoFolder(cryptoFolder.getParent(), cryptoFolder.getName(), cryptoFolder.getPath(), dirFile);
	}

	boolean exists(CloudNode node) throws BackendException {
		if (node instanceof CryptoFolder) {
			return exists((CryptoFolder) node);
		} else if (node instanceof CryptoFile) {
			return exists((CryptoFile) node);
		} else if (node instanceof CryptoSymlink) {
			return exists((CryptoSymlink) node);
		} else {
			throw new IllegalArgumentException("Unexpected CloudNode type: " + node.getClass());
		}
	}

	private boolean exists(CryptoFolder folder) throws BackendException {
		return cloudContentRepository.exists(folder.getDirFile()) && cloudContentRepository.exists(dirIdInfo(folder).getCloudFolder());
	}

	private boolean exists(CryptoFile file) throws BackendException {
		return cloudContentRepository.exists(file.getCloudFile());
	}

	private boolean exists(CryptoSymlink symlink) throws BackendException {
		return cloudContentRepository.exists(symlink.getCloudFile());
	}

	void assertCryptoFolderAlreadyExists(CryptoFolder cryptoFolder) throws BackendException {
		if (cloudContentRepository.exists(cryptoFolder.getDirFile()) //
				|| cloudContentRepository.exists(file(cryptoFolder.getParent(), cryptoFolder.getName()))) {
			throw new CloudNodeAlreadyExistsException(cryptoFolder.getName());
		}
	}

	void assertCryptoFileAlreadyExists(CryptoFile cryptoFile) throws BackendException {
		if (cloudContentRepository.exists(cryptoFile.getCloudFile()) //
				|| cloudContentRepository.exists(folder(cryptoFile.getParent(), cryptoFile.getName()).getDirFile())) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}
	}

	private CryptoFile writeFromTmpFile(DataSource originalDataSource, final CryptoFile cryptoFile, File encryptedFile, final ProgressAware<UploadState> progressAware, boolean replace) throws BackendException, IOException {
		CryptoFile targetFile = targetFile(cryptoFile, replace);
		return file(targetFile, //
				cloudContentRepository.write( //
						targetFile.getCloudFile(), //
						originalDataSource.decorate(FileBasedDataSource.from(encryptedFile)), //
						new UploadFileReplacingProgressAware(cryptoFile, progressAware), //
						replace, //
						encryptedFile.length()), //
				cryptoFile.getSize());
	}

	private CryptoFile targetFile(CryptoFile cryptoFile, boolean replace) throws BackendException {
		if (replace || !cloudContentRepository.exists(cryptoFile)) {
			return cryptoFile;
		}
		return firstNonExistingAutoRenamedFile(cryptoFile);
	}

	private CryptoFile firstNonExistingAutoRenamedFile(CryptoFile original) throws BackendException {
		String name = original.getName();
		String nameWithoutExtension = nameWithoutExtension(name);
		String extension = extension(name);
		int counter = 1;
		CryptoFile result;
		do {
			String newFileName = nameWithoutExtension + " (" + counter + ")" + extension;
			result = file(original.getParent(), newFileName, original.getSize());
			counter++;
		} while (cloudContentRepository.exists(result));
		return result;
	}

	String nameWithoutExtension(String name) {
		int lastDot = name.lastIndexOf(".");
		if (lastDot == -1) {
			return name;
		}
		return name.substring(0, lastDot);
	}

	String extension(String name) {
		int lastDot = name.lastIndexOf(".");
		if (lastDot == -1) {
			return "";
		}
		return name.substring(lastDot + 1);
	}

	public void read(CryptoFile cryptoFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		CloudFile ciphertextFile = cryptoFile.getCloudFile();
		try {
			File encryptedTmpFile = readToTmpFile(cryptoFile, ciphertextFile, progressAware);
			progressAware.onProgress(Progress.started(DownloadState.decryption(cryptoFile)));
			try (ReadableByteChannel readableByteChannel = Channels.newChannel(new FileInputStream(encryptedTmpFile)); //
				 ReadableByteChannel decryptingReadableByteChannel = new DecryptingReadableByteChannel(readableByteChannel, cryptor(), true)) {
				ByteBuffer buff = ByteBuffer.allocate(cryptor().fileContentCryptor().ciphertextChunkSize());
				long cleartextSize = cryptoFile.getSize().orElse(Long.MAX_VALUE);
				long decrypted = 0;
				int read;
				while ((read = decryptingReadableByteChannel.read(buff)) > 0) {
					buff.flip();
					data.write(buff.array(), 0, buff.remaining());
					decrypted += read;
					progressAware.onProgress(progress(DownloadState.decryption(cryptoFile)).between(0).and(cleartextSize).withValue(decrypted));
				}
			} finally {
				encryptedTmpFile.delete();
				progressAware.onProgress(Progress.completed(DownloadState.decryption(cryptoFile)));
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private File readToTmpFile(CryptoFile cryptoFile, CloudFile file, ProgressAware progressAware) throws BackendException, IOException {
		File encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", getInternalCache());
		try (OutputStream encryptedData = new FileOutputStream(encryptedTmpFile)) {
			cloudContentRepository.read(file, Optional.of(encryptedTmpFile), encryptedData, new DownloadFileReplacingProgressAware(cryptoFile, progressAware));
			return encryptedTmpFile;
		}
	}

	public String currentAccount(Cloud cloud) throws BackendException {
		return cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}

	DirIdInfo dirIdInfo(CryptoFolder folder) throws BackendException {
		DirIdInfo dirIdInfo = dirIdCache.get(folder);
		if (dirIdInfo == null) {
			return createDirIdInfo(folder);
		}
		return dirIdInfo;
	}

	DirIdInfo createDirIdInfoFor(String dirId) throws BackendException {
		String dirHash = dirHash(dirId);
		CloudFolder lvl2Dir = lvl2Dir(dirHash);
		return new DirIdInfo(dirId, lvl2Dir);
	}

	byte[] loadContentsOfDirFile(CryptoFolder folder) throws BackendException, EmptyDirFileException {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cloudContentRepository.read(folder.getDirFile(), Optional.empty(), out, NO_OP_PROGRESS_AWARE);
			if (dirfileIsEmpty(out)) {
				throw new EmptyDirFileException(folder.getName(), folder.getDirFile().getPath());
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	String newDirId() {
		return UUID.randomUUID().toString();
	}

	boolean dirfileIsEmpty(ByteArrayOutputStream out) {
		return out.size() == 0;
	}

	private CloudFolder lvl2Dir(String dirHash) throws BackendException {
		return cloudContentRepository.folder(lvl1Dir(dirHash), dirHash.substring(2));
	}

	private CloudFolder lvl1Dir(String dirHash) throws BackendException {
		return cloudContentRepository.folder(dataFolder(), dirHash.substring(0, 2));
	}

	Cryptor cryptor() {
		return cryptor.get();
	}

	CloudFolder storageLocation() {
		return storageLocation;
	}

	void addFolderToCache(CryptoFolder result, DirIdCache.DirIdInfo dirInfo) {
		dirIdCache.put(result, dirInfo);
	}

	void evictFromCache(CryptoFolder cryptoFolder) {
		dirIdCache.evict(cryptoFolder);
	}

	CryptoFile writeShortNameFile(CryptoFile cryptoFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException {
		if (!replace) {
			assertCryptoFileAlreadyExists(cryptoFile);
		}
		try (InputStream stream = data.open(context)) {
			File encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", getInternalCache());
			try (WritableByteChannel writableByteChannel = Channels.newChannel(new FileOutputStream(encryptedTmpFile)); //
				 WritableByteChannel encryptingWritableByteChannel = new EncryptingWritableByteChannel(writableByteChannel, cryptor())) {
				progressAware.onProgress(Progress.started(UploadState.encryption(cryptoFile)));
				ByteBuffer buff = ByteBuffer.allocate(cryptor().fileContentCryptor().cleartextChunkSize());
				long ciphertextSize = Cryptors.ciphertextSize(cryptoFile.getSize().get(), cryptor()) + cryptor().fileHeaderCryptor().headerSize();
				int read;
				long encrypted = 0;
				while ((read = stream.read(buff.array())) > 0) {
					buff.limit(read);
					int written = encryptingWritableByteChannel.write(buff);
					buff.flip();
					encrypted += written;
					progressAware.onProgress(progress(UploadState.encryption(cryptoFile)).between(0).and(ciphertextSize).withValue(encrypted));
				}
				encryptingWritableByteChannel.close();
				progressAware.onProgress(Progress.completed(UploadState.encryption(cryptoFile)));
				return writeFromTmpFile(data, cryptoFile, encryptedTmpFile, progressAware, replace);
			} catch (Throwable e) {
				throw e;
			} finally {
				encryptedTmpFile.delete();
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}
}
