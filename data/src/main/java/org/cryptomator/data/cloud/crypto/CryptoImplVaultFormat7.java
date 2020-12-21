package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import com.google.common.io.BaseEncoding;

import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.EncryptingWritableByteChannel;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.common.MessageDigestSupplier;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.EmptyDirFileException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoDirFileException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.SymLinkException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.UploadFileReplacingProgressAware;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.Encodings.UTF_8;

final class CryptoImplVaultFormat7 extends CryptoImplDecorator {

	private static final int SHORT_NAMES_MAX_LENGTH = 220;
	private static final String CLOUD_NODE_EXT = ".c9r";
	private static final String LONG_NODE_FILE_EXT = ".c9s";
	private static final String CLOUD_FOLDER_DIR_FILE_PRE = "dir";
	private static final String LONG_NODE_FILE_CONTENT_CONTENTS = "contents";
	private static final String LONG_NODE_FILE_CONTENT_NAME = "name";
	private static final String CLOUD_NODE_SYMLINK_PRE = "symlink";
	private static final Pattern BASE64_ENCRYPTED_NAME_PATTERN = Pattern.compile("^([A-Za-z0-9+/\\-_]{4})*([A-Za-z0-9+/\\-]{4}|[A-Za-z0-9+/\\-_]{3}=|[A-Za-z0-9+/\\-_]{2}==)?$");

	private static final BaseEncoding BASE64 = BaseEncoding.base64Url();

	CryptoImplVaultFormat7(Context context, Supplier<Cryptor> cryptor, CloudContentRepository cloudContentRepository, CloudFolder storageLocation, DirIdCache dirIdCache) {
		super(context, cryptor, cloudContentRepository, storageLocation, dirIdCache);
	}

	@Override
	CryptoFolder folder(CryptoFolder cryptoParent, String cleartextName) throws BackendException {
		String dirFileName = encryptFolderName(cryptoParent, cleartextName);
		CloudFolder dirFolder = cloudContentRepository.folder(dirIdInfo(cryptoParent).getCloudFolder(), dirFileName);
		CloudFile dirFile = cloudContentRepository.file(dirFolder, CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT);
		return folder(cryptoParent, cleartextName, dirFile);
	}

	@Override
	String encryptName(CryptoFolder cryptoFolder, String name) throws BackendException {
		String ciphertextName = cryptor() //
				.fileNameCryptor() //
				.encryptFilename(BASE64, name, dirIdInfo(cryptoFolder).getId().getBytes(UTF_8)) + CLOUD_NODE_EXT;

		if (ciphertextName.length() > SHORT_NAMES_MAX_LENGTH) {
			ciphertextName = deflate(cryptoFolder, ciphertextName);
		}
		return ciphertextName;
	}

	private String deflate(CryptoFolder cryptoParent, String longFileName) throws BackendException {
		byte[] longFilenameBytes = longFileName.getBytes(UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortFileName = BASE64.encode(hash) + LONG_NODE_FILE_EXT;

		CloudFolder dirFolder = cloudContentRepository.folder(dirIdInfo(cryptoParent).getCloudFolder(), shortFileName);

		// if folder already exists in case of renaming
		if (!cloudContentRepository.exists(dirFolder)) {
			dirFolder = cloudContentRepository.create(dirFolder);
		}

		byte[] data = longFileName.getBytes(UTF_8);
		CloudFile cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_NAME + LONG_NODE_FILE_EXT, Optional.of((long) data.length));
		cloudContentRepository.write(cloudFile, ByteArrayDataSource.from(data), NO_OP_PROGRESS_AWARE, true, data.length);
		return shortFileName;
	}

	private CloudFile metadataFile(CloudNode cloudNode) throws BackendException {
		CloudFolder cloudFolder;

		if (cloudNode instanceof CloudFile) {
			cloudFolder = cloudNode.getParent();
		} else if (cloudNode instanceof CloudFolder) {
			cloudFolder = (CloudFolder) cloudNode;
		} else {
			throw new IllegalStateException("Should be file or folder");
		}

		return cloudContentRepository.file(cloudFolder, LONG_NODE_FILE_CONTENT_NAME + LONG_NODE_FILE_EXT);
	}

	private String inflate(CloudNode cloudNode) throws BackendException {
		CloudFile metadataFile = metadataFile(cloudNode);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cloudContentRepository.read(metadataFile, Optional.empty(), out, NO_OP_PROGRESS_AWARE);
		return new String(out.toByteArray(), UTF_8);
	}

	@Override
	String decryptName(String dirId, String encryptedName) {
		Optional<String> ciphertextName = extractEncryptedName(encryptedName);
		if (ciphertextName.isPresent()) {
			return cryptor().fileNameCryptor().decryptFilename(BASE64, ciphertextName.get(), dirId.getBytes(UTF_8));
		} else {
			return null;
		}
	}

	@Override
	List<CryptoNode> list(CryptoFolder cryptoFolder) throws BackendException {
		dirIdCache.evictSubFoldersOf(cryptoFolder);

		DirIdCache.DirIdInfo dirIdInfo = dirIdInfo(cryptoFolder);
		String dirId = dirIdInfo(cryptoFolder).getId();
		CloudFolder lvl2Dir = dirIdInfo.getCloudFolder();

		List<CloudNode> ciphertextNodes;

		try {
			ciphertextNodes = cloudContentRepository.list(lvl2Dir);
		} catch (NoSuchCloudFileException e) {
			if (cryptoFolder instanceof RootCryptoFolder) {
				Timber.tag("CryptoFs").e("No lvl2Dir exists for root folder in %s", lvl2Dir.getPath());
				throw new FatalBackendException(String.format("No lvl2Dir exists for root folder in %s", lvl2Dir.getPath()), e);
			} else if (cloudContentRepository.exists(cloudContentRepository.file(cryptoFolder.getDirFile().getParent(), CLOUD_NODE_SYMLINK_PRE + CLOUD_NODE_EXT))) {
				throw new SymLinkException();
			} else if (!cloudContentRepository.exists(cryptoFolder.getDirFile())) {
				Timber.tag("CryptoFs").e("No dir file exists in %s", cryptoFolder.getDirFile().getPath());
				throw new NoDirFileException(cryptoFolder.getName(), cryptoFolder.getDirFile().getPath());
			}
			return Collections.emptyList();
		}

		List<CryptoNode> result = new ArrayList<>();
		for (CloudNode node : ciphertextNodes) {
			ciphertextToCleartextNode(cryptoFolder, dirId, node).ifPresent(result::add);
		}

		return result;
	}

	private Optional<CryptoNode> ciphertextToCleartextNode(CryptoFolder cryptoFolder, String dirId, CloudNode cloudNode) throws BackendException {
		String ciphertextName = cloudNode.getName();
		Optional<CloudFile> longNameFolderDirFile = Optional.empty();
		Optional<CloudFile> longNameFile = Optional.empty();

		if (ciphertextName.endsWith(CLOUD_NODE_EXT)) {
			ciphertextName = nameWithoutExtension(ciphertextName);
		} else if (ciphertextName.endsWith(LONG_NODE_FILE_EXT)) {
			Optional<String> ciphertextNameOption = longNodeCiphertextName(cloudNode);
			if (ciphertextNameOption.isPresent()) {
				ciphertextName = ciphertextNameOption.get();
			} else {
				return Optional.empty();
			}

			List<CloudNode> subfiles = cloudContentRepository.list((CloudFolder) cloudNode);

			for (CloudNode cloudNode1 : subfiles) {
				switch (cloudNode1.getName()) {
				case LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT:
					longNameFile = Optional.of((CloudFile) cloudNode1);
					break;
				case CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT:
					longNameFolderDirFile = Optional.of((CloudFile) cloudNode1);
					break;
				case CLOUD_NODE_SYMLINK_PRE + CLOUD_NODE_EXT:
					return Optional.empty();
				}
			}
		}

		try {
			String cleartextName = decryptName(dirId, ciphertextName);

			if (cleartextName == null) {
				Timber.tag("CryptoFs").w("Failed to parse cipher text name of: %s", cloudNode.getPath());
				return Optional.empty();
			}

			return cloudNodeFromName(cloudNode, cryptoFolder, cleartextName, longNameFile, longNameFolderDirFile);
		} catch (AuthenticationFailedException e) {
			Timber.tag("CryptoFs").w(e, "File/Folder name authentication failed: %s", cloudNode.getPath());
			return Optional.empty();
		} catch (IllegalArgumentException e) {
			Timber.tag("CryptoFs").w(e, "Illegal ciphertext filename/folder: %s", cloudNode.getPath());
			return Optional.empty();
		}
	}

	private Optional<CryptoNode> cloudNodeFromName(CloudNode cloudNode, CryptoFolder cryptoFolder, String cleartextName, Optional<CloudFile> longNameFile, Optional<CloudFile> dirFile) throws BackendException {
		if (cloudNode instanceof CloudFile) {
			CloudFile cloudFile = (CloudFile) cloudNode;
			Optional<Long> cleartextSize = Optional.empty();
			if (cloudFile.getSize().isPresent()) {
				long ciphertextSizeWithoutHeader = cloudFile.getSize().get() - cryptor().fileHeaderCryptor().headerSize();
				if (ciphertextSizeWithoutHeader >= 0) {
					cleartextSize = Optional.of(Cryptors.cleartextSize(ciphertextSizeWithoutHeader, cryptor()));
				}
			}
			return Optional.of(file(cryptoFolder, cleartextName, cloudFile, cleartextSize));
		} else if (cloudNode instanceof CloudFolder) {
			if (longNameFile.isPresent()) {
				// long file
				Optional<Long> cleartextSize = Optional.empty();
				if (longNameFile.get().getSize().isPresent()) {
					long ciphertextSizeWithoutHeader = longNameFile.get().getSize().get() - cryptor().fileHeaderCryptor().headerSize();
					if (ciphertextSizeWithoutHeader >= 0) {
						cleartextSize = Optional.of(Cryptors.cleartextSize(ciphertextSizeWithoutHeader, cryptor()));
					}
				}

				return Optional.of(file(cryptoFolder, cleartextName, longNameFile.get(), cleartextSize));
			} else {
				// folder
				if (dirFile.isPresent()) {
					return Optional.of(folder(cryptoFolder, cleartextName, dirFile.get()));
				} else {
					CloudFile constructedDirFile = cloudContentRepository.file((CloudFolder) cloudNode, "dir" + CLOUD_NODE_EXT);
					return Optional.of(folder(cryptoFolder, cleartextName, constructedDirFile));
				}
			}
		}

		return Optional.empty();
	}

	private Optional<String> longNodeCiphertextName(CloudNode cloudNode) {
		try {
			String ciphertextName = inflate(cloudNode);
			ciphertextName = nameWithoutExtension(ciphertextName);
			return Optional.of(ciphertextName);
		} catch (NoSuchCloudFileException e) {
			Timber.tag("CryptoFs").e("Missing %s%s for cloud node: %s", LONG_NODE_FILE_CONTENT_NAME, LONG_NODE_FILE_EXT, cloudNode.getPath());
			return Optional.empty();
		} catch (BackendException e) {
			Timber.tag("CryptoFs").e(e, "Failed to read %s%s for cloud node: %s", LONG_NODE_FILE_CONTENT_NAME, LONG_NODE_FILE_EXT, cloudNode.getPath());
			return Optional.empty();
		}
	}

	@Override
	DirIdCache.DirIdInfo createDirIdInfo(CryptoFolder folder) throws BackendException {
		String dirId = loadDirId(folder);
		return dirIdCache.put(folder, createDirIdInfoFor(dirId));
	}

	@Override
	String encryptFolderName(CryptoFolder cryptoFolder, String name) throws BackendException {
		return encryptName(cryptoFolder, name);
	}

	@Override
	CryptoSymlink symlink(CryptoFolder cryptoParent, String cleartextName, String target) throws BackendException {
		return null;
	}

	@Override
	String loadDirId(CryptoFolder folder) throws BackendException, EmptyDirFileException {
		CloudFile dirFile = null;

		if (folder.getDirFile() != null) {
			dirFile = folder.getDirFile();
		}

		if (RootCryptoFolder.isRoot(folder)) {
			return CryptoConstants.ROOT_DIR_ID;
		} else if (dirFile != null && cloudContentRepository.exists(dirFile)) {
			return new String(loadContentsOfDirFile(dirFile), UTF_8);
		} else {
			return newDirId();
		}
	}

	private byte[] loadContentsOfDirFile(CloudFile file) throws BackendException, EmptyDirFileException {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			cloudContentRepository.read(file, Optional.empty(), out, NO_OP_PROGRESS_AWARE);
			if (dirfileIsEmpty(out)) {
				throw new EmptyDirFileException(file.getName(), file.getPath());
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	CryptoFolder create(CryptoFolder folder) throws BackendException {
		boolean shortName = false;
		if (folder.getDirFile().getParent().getName().endsWith(LONG_NODE_FILE_EXT)) {
			assertCryptoLongDirFileAlreadyExists(folder);
		} else {
			assertCryptoFolderAlreadyExists(folder);
			shortName = true;
		}

		DirIdCache.DirIdInfo dirIdInfo = dirIdInfo(folder);
		CloudFolder createdCloudFolder = cloudContentRepository.create(dirIdInfo.getCloudFolder());

		CloudFolder dirFolder = folder.getDirFile().getParent();
		CloudFile dirFile = folder.getDirFile();
		if (shortName) {
			dirFolder = cloudContentRepository.create(dirFolder);
			dirFile = cloudContentRepository.file(dirFolder, folder.getDirFile().getName());
		}

		byte[] dirId = dirIdInfo.getId().getBytes(UTF_8);
		CloudFile createdDirFile = cloudContentRepository.write(dirFile, ByteArrayDataSource.from(dirId), NO_OP_PROGRESS_AWARE, false, dirId.length);
		CryptoFolder result = folder(folder, createdDirFile);
		addFolderToCache(result, dirIdInfo.withCloudFolder(createdCloudFolder));
		return result;
	}

	@Override
	Optional<String> extractEncryptedName(String ciphertextName) {
		final Matcher matcher = BASE64_ENCRYPTED_NAME_PATTERN.matcher(ciphertextName);
		if (matcher.find(0)) {
			return Optional.of(matcher.group());
		} else {
			return Optional.empty();
		}
	}

	@Override
	CryptoFolder move(CryptoFolder source, CryptoFolder target) throws BackendException {
		boolean shortName = false;
		if (target.getDirFile().getParent().getName().endsWith(LONG_NODE_FILE_EXT)) {
			assertCryptoLongDirFileAlreadyExists(target);
		} else {
			assertCryptoFolderAlreadyExists(target);
			shortName = true;
		}

		CloudFile targetDirFile = target.getDirFile();
		if (shortName) {
			CloudFolder targetDirFolder = cloudContentRepository.create(target.getDirFile().getParent());
			targetDirFile = cloudContentRepository.file(targetDirFolder, target.getDirFile().getName());
		}

		CryptoFolder result = folder(target.getParent(), target.getName(), cloudContentRepository.move(source.getDirFile(), targetDirFile));

		cloudContentRepository.delete(source.getDirFile().getParent());

		evictFromCache(source);
		evictFromCache(target);

		return result;
	}

	@Override
	CryptoFile move(CryptoFile source, CryptoFile target) throws BackendException {
		if (source.getCloudFile().getParent().getName().endsWith(LONG_NODE_FILE_EXT)) {
			CloudFolder targetDirFolder = cloudContentRepository.folder(target.getCloudFile().getParent(), target.getCloudFile().getName());
			CryptoFile cryptoFile;
			if (target.getCloudFile().getName().endsWith(LONG_NODE_FILE_EXT)) {
				assertCryptoLongDirFileAlreadyExists(targetDirFolder);
				cryptoFile = moveLongFileToLongFile(source, target, targetDirFolder);
			} else {
				assertCryptoFileAlreadyExists(target);
				cryptoFile = moveLongFileToShortFile(source, target);
			}
			CloudFolder sourceDirFolder = cloudContentRepository.folder(source.getCloudFile().getParent().getParent(), source.getCloudFile().getParent().getName());
			cloudContentRepository.delete(sourceDirFolder);
			return cryptoFile;
		} else {
			CloudFolder targetDirFolder = cloudContentRepository.folder(target.getCloudFile().getParent(), target.getCloudFile().getName());
			if (target.getCloudFile().getName().endsWith(LONG_NODE_FILE_EXT)) {
				assertCryptoLongDirFileAlreadyExists(targetDirFolder);
				return moveShortFileToLongFile(source, target, targetDirFolder);
			} else {
				assertCryptoFileAlreadyExists(target);
				return file(target, cloudContentRepository.move(source.getCloudFile(), target.getCloudFile()), source.getSize());
			}
		}
	}

	private CryptoFile moveLongFileToLongFile(CryptoFile source, CryptoFile target, CloudFolder targetDirFolder) throws BackendException {
		CloudFile sourceFile = cloudContentRepository.file(source.getCloudFile().getParent(), LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT);
		CloudFile movedFile = cloudContentRepository.move(sourceFile, cloudContentRepository.file(targetDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT));
		return file(target, movedFile, movedFile.getSize());
	}

	private CryptoFile moveLongFileToShortFile(CryptoFile source, CryptoFile target) throws BackendException {
		CloudFile sourceFile = cloudContentRepository.file(source.getCloudFile().getParent(), LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT);
		CloudFile movedFile = cloudContentRepository.move(sourceFile, target.getCloudFile());
		return file(target, movedFile, movedFile.getSize());
	}

	private CryptoFile moveShortFileToLongFile(CryptoFile source, CryptoFile target, CloudFolder targetDirFolder) throws BackendException {
		CloudFile movedFile = cloudContentRepository.move(source.getCloudFile(), cloudContentRepository.file(targetDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT));
		return file(target, movedFile, movedFile.getSize());
	}

	@Override
	void delete(CloudNode node) throws BackendException {
		if (node instanceof CryptoFolder) {
			CryptoFolder cryptoFolder = (CryptoFolder) node;
			List<CryptoFolder> cryptoSubfolders = deepCollectSubfolders(cryptoFolder);
			for (CryptoFolder cryptoSubfolder : cryptoSubfolders) {
				try {
					cloudContentRepository.delete(dirIdInfo(cryptoSubfolder).getCloudFolder());
				} catch (NoSuchCloudFileException e) {
					// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
				}
			}

			try {
				cloudContentRepository.delete(dirIdInfo(cryptoFolder).getCloudFolder());
			} catch (NoSuchCloudFileException e) {
				// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
			}

			cloudContentRepository.delete(cryptoFolder.getDirFile().getParent());

			evictFromCache(cryptoFolder);
		} else if (node instanceof CryptoFile) {
			CryptoFile cryptoFile = (CryptoFile) node;
			if (cryptoFile.getCloudFile().getParent().getName().endsWith(LONG_NODE_FILE_EXT)) {
				cloudContentRepository.delete(cryptoFile.getCloudFile().getParent());
			} else {
				cloudContentRepository.delete(cryptoFile.getCloudFile());
			}
		}
	}

	@Override
	public CryptoFile write(CryptoFile cryptoFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException {
		if (cryptoFile.getCloudFile().getName().endsWith(LONG_NODE_FILE_EXT)) {
			return writeLongFile(cryptoFile, data, progressAware, replace, length);
		} else {
			return writeShortNameFile(cryptoFile, data, progressAware, replace, length);
		}
	}

	private CryptoFile writeLongFile(CryptoFile cryptoFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException {
		CloudFolder dirFolder = cloudContentRepository.folder(cryptoFile.getCloudFile().getParent(), cryptoFile.getCloudFile().getName());
		CloudFile cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT, data.size(context));

		assertCryptoLongDirFileAlreadyExists(dirFolder);

		try (InputStream stream = data.open(context)) {
			File encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", getInternalCache());
			try (WritableByteChannel writableByteChannel = Channels.newChannel(new FileOutputStream(encryptedTmpFile)); //
					WritableByteChannel encryptingWritableByteChannel = new EncryptingWritableByteChannel(writableByteChannel, cryptor())) {
				progressAware.onProgress(Progress.started(UploadState.encryption(cloudFile)));
				ByteBuffer buff = ByteBuffer.allocate(cryptor().fileContentCryptor().cleartextChunkSize());
				long ciphertextSize = Cryptors.ciphertextSize(cloudFile.getSize().get(), cryptor()) + cryptor().fileHeaderCryptor().headerSize();
				int read;
				long encrypted = 0;
				while ((read = stream.read(buff.array())) > 0) {
					buff.limit(read);
					int written = encryptingWritableByteChannel.write(buff);
					buff.flip();
					encrypted += written;
					progressAware.onProgress(progress(UploadState.encryption(cloudFile)).between(0).and(ciphertextSize).withValue(encrypted));
				}
				encryptingWritableByteChannel.close();
				progressAware.onProgress(Progress.completed(UploadState.encryption(cloudFile)));

				CloudFile targetFile = targetFile(cryptoFile, cloudFile, replace);

				return file(cryptoFile, //
						cloudContentRepository.write( //
								targetFile, //
								data.decorate(FileBasedDataSource.from(encryptedTmpFile)), //
								new UploadFileReplacingProgressAware(cryptoFile, progressAware), //
								replace, //
								encryptedTmpFile.length()), //
						cryptoFile.getSize());
			} catch (Throwable e) {
				throw e;
			} finally {
				encryptedTmpFile.delete();
			}
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private CloudFile targetFile(CryptoFile cryptoFile, CloudFile cloudFile, boolean replace) throws BackendException {
		if (replace || !cloudContentRepository.exists(cloudFile)) {
			return cloudFile;
		}
		return firstNonExistingAutoRenamedFile(cryptoFile);
	}

	private CloudFile firstNonExistingAutoRenamedFile(CryptoFile original) throws BackendException {
		String name = original.getName();
		String nameWithoutExtension = nameWithoutExtension(name);
		String extension = extension(name);

		if (!extension.isEmpty()) {
			extension = "." + extension;
		}

		int counter = 1;
		CryptoFile result;
		CloudFile cloudFile;
		do {
			String newFileName = nameWithoutExtension + " (" + counter + ")" + extension;
			result = file(original.getParent(), newFileName, original.getSize());
			counter++;

			CloudFolder dirFolder = cloudContentRepository.folder(result.getCloudFile().getParent(), result.getCloudFile().getName());
			cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT, result.getSize());
		} while (cloudContentRepository.exists(cloudFile));
		return cloudFile;
	}

	private void assertCryptoLongDirFileAlreadyExists(CloudFolder cryptoFolder) throws BackendException {
		if (cloudContentRepository.exists(cloudContentRepository.file(cryptoFolder, CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT))) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}
	}
}
