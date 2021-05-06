package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.BaseNCodec;
import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.api.AuthenticationFailedException;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.common.MessageDigestSupplier;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.AlreadyExistException;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.EmptyDirFileException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.cryptomator.util.Encodings.UTF_8;

final class CryptoImplVaultFormatPre7 extends CryptoImplDecorator {

	static final int SHORTENING_THRESHOLD = 129;
	private static final String DIR_PREFIX = "0";
	private static final String SYMLINK_PREFIX = "1S";
	private static final String LONG_NAME_FILE_EXT = ".lng";
	private static final String METADATA_DIR_NAME = "m";
	private static final BaseNCodec BASE32 = new Base32();
	private static final Pattern BASE32_ENCRYPTED_NAME_PATTERN = Pattern.compile("^(0|1S)?(([A-Z2-7]{8})*[A-Z2-7=]{8})$");

	CryptoImplVaultFormatPre7(Context context, Supplier<Cryptor> cryptor, CloudContentRepository cloudContentRepository, CloudFolder storageLocation, DirIdCache dirIdCache) {
		super(context, cryptor, cloudContentRepository, storageLocation, dirIdCache, SHORTENING_THRESHOLD);
	}

	@Override
	CryptoFolder folder(CryptoFolder cryptoParent, String cleartextName) throws BackendException {
		String dirFileName = encryptFolderName(cryptoParent, cleartextName);
		CloudFile dirFile = cloudContentRepository.file(dirIdInfo(cryptoParent).getCloudFolder(), dirFileName);
		return folder(cryptoParent, cleartextName, dirFile);
	}

	@Override
	CryptoFolder create(CryptoFolder folder) throws BackendException {
		assertCryptoFolderAlreadyExists(folder);
		DirIdCache.DirIdInfo dirIdInfo = dirIdInfo(folder);
		CloudFolder createdCloudFolder = cloudContentRepository.create(dirIdInfo.getCloudFolder());
		byte[] dirId = dirIdInfo.getId().getBytes(UTF_8);
		CloudFile createdDirFile = cloudContentRepository.write(folder.getDirFile(), ByteArrayDataSource.from(dirId), NO_OP_PROGRESS_AWARE, false, dirId.length);
		CryptoFolder result = folder(folder, createdDirFile);
		addFolderToCache(result, dirIdInfo.withCloudFolder(createdCloudFolder));
		return result;
	}

	@Override
	String encryptName(CryptoFolder cryptoParent, String name) throws BackendException {
		return encryptName(cryptoParent, name, "");
	}

	private String encryptName(CryptoFolder cryptoParent, String name, String prefix) throws BackendException {
		String ciphertextName = prefix + cryptor().fileNameCryptor().encryptFilename(name, dirIdInfo(cryptoParent).getId().getBytes(UTF_8));
		if (ciphertextName.length() > shorteningThreshold) {
			ciphertextName = deflate(ciphertextName);
		}
		return ciphertextName;
	}

	private String deflate(String longFileName) throws BackendException {
		byte[] longFilenameBytes = longFileName.getBytes(UTF_8);
		byte[] hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes);
		String shortFileName = BASE32.encodeAsString(hash) + LONG_NAME_FILE_EXT;
		CloudFile metadataFile = metadataFile(shortFileName);
		byte[] data = longFileName.getBytes(UTF_8);
		try {
			cloudContentRepository.create(metadataFile.getParent());
		} catch (AlreadyExistException e) {
		}
		cloudContentRepository.write(metadataFile, ByteArrayDataSource.from(data), NO_OP_PROGRESS_AWARE, true, data.length);
		return shortFileName;
	}

	private String inflate(String shortFileName) throws BackendException {
		CloudFile metadataFile = metadataFile(shortFileName);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		cloudContentRepository.read(metadataFile, Optional.empty(), out, NO_OP_PROGRESS_AWARE);
		return new String(out.toByteArray(), UTF_8);
	}

	private CloudFile inflatePermanently(CloudFile cloudFile, String longFileName) throws BackendException {
		Timber.tag("CryptoFs").i("inflatePermanently: %s -> %s", cloudFile.getName(), longFileName);
		CloudFile newCiphertextFile = cloudContentRepository.file(cloudFile.getParent(), longFileName);
		cloudContentRepository.move(cloudFile, newCiphertextFile);
		return newCiphertextFile;
	}

	private CloudFile metadataFile(String shortFilename) throws BackendException {
		CloudFolder firstLevelFolder = cloudContentRepository.folder(metadataFolder(), shortFilename.substring(0, 2));
		CloudFolder secondLevelFolder = cloudContentRepository.folder(firstLevelFolder, shortFilename.substring(2, 4));
		return cloudContentRepository.file(secondLevelFolder, shortFilename);
	}

	private CloudFolder metadataFolder() throws BackendException {
		return cloudContentRepository.folder(storageLocation(), METADATA_DIR_NAME);
	}

	@Override
	List<CryptoNode> list(CryptoFolder cryptoFolder) throws BackendException {
		DirIdCache.DirIdInfo dirIdInfo = dirIdInfo(cryptoFolder);
		String dirId = dirIdInfo(cryptoFolder).getId();
		CloudFolder lvl2Dir = dirIdInfo.getCloudFolder();
		List<CloudNode> ciphertextNodes = cloudContentRepository.list(lvl2Dir);
		List<CryptoNode> result = new ArrayList<>();
		for (CloudNode node : ciphertextNodes) {
			if (node instanceof CloudFile) {
				ciphertextToCleartextNode(cryptoFolder, dirId, node).ifPresent(result::add);
			}
		}
		return result;
	}

	private Optional<CryptoNode> ciphertextToCleartextNode(CryptoFolder cryptoFolder, String dirId, CloudNode cloudNode) throws BackendException {
		CloudFile cloudFile = (CloudFile) cloudNode;
		String ciphertextName = cloudFile.getName();
		if (ciphertextName.endsWith(LONG_NAME_FILE_EXT)) {
			try {
				ciphertextName = inflate(ciphertextName);
				if (ciphertextName.length() <= shorteningThreshold) {
					cloudFile = inflatePermanently(cloudFile, ciphertextName);
				}
			} catch (NoSuchCloudFileException e) {
				Timber.tag("CryptoFs").e("Missing mFile: %s", ciphertextName);
				return Optional.empty();
			} catch (BackendException e) {
				Timber.tag("CryptoFs").e(e, "Failed to read mFile: %s", ciphertextName);
				return Optional.empty();
			}
		}
		String cleartextName;
		try {
			cleartextName = decryptName(dirId, ciphertextName.toUpperCase());
		} catch (AuthenticationFailedException e) {
			Timber.tag("CryptoFs").w("File name authentication failed: %s", cloudFile.getPath());
			return Optional.empty();
		} catch (IllegalArgumentException e) {
			Timber.tag("CryptoFs").d("Illegal ciphertext filename: %s", cloudFile.getPath());
			return Optional.empty();
		}
		if (cleartextName == null || ciphertextName.startsWith(SYMLINK_PREFIX)) {
			return Optional.empty();
		} else if (ciphertextName.startsWith(DIR_PREFIX)) {
			return Optional.of(folder(cryptoFolder, cleartextName, cloudFile));
		} else {
			Optional<Long> cleartextSize = Optional.empty();
			if (cloudFile.getSize().isPresent()) {
				long ciphertextSizeWithoutHeader = cloudFile.getSize().get() - cryptor().fileHeaderCryptor().headerSize();
				if (ciphertextSizeWithoutHeader >= 0) {
					cleartextSize = Optional.of(Cryptors.cleartextSize(ciphertextSizeWithoutHeader, cryptor()));
				}
			}
			return Optional.of(file(cryptoFolder, cleartextName, cloudFile, cleartextSize));
		}
	}

	@Override
	String decryptName(String dirId, String encryptedName) {
		Optional<String> ciphertextName = extractEncryptedName(encryptedName);
		if (ciphertextName.isPresent()) {
			return cryptor().fileNameCryptor().decryptFilename(ciphertextName.get(), dirId.getBytes(UTF_8));
		} else {
			return null;
		}
	}

	@Override
	Optional<String> extractEncryptedName(String ciphertextName) {
		Matcher matcher = BASE32_ENCRYPTED_NAME_PATTERN.matcher(ciphertextName);
		if (matcher.find(0)) {
			return Optional.of(matcher.group(2));
		} else {
			return Optional.empty();
		}
	}

	@Override
	CryptoSymlink symlink(CryptoFolder cryptoParent, String cleartextName, String target) throws BackendException {
		String ciphertextName = encryptSymlinkName(cryptoParent, cleartextName);
		CloudFile cloudFile = cloudContentRepository.file(dirIdInfo(cryptoParent).getCloudFolder(), ciphertextName);
		return new CryptoSymlink(cryptoParent, cleartextName, path(cryptoParent, cleartextName), target, cloudFile);
	}

	private String encryptSymlinkName(CryptoFolder cryptoFolder, String name) throws BackendException {
		return encryptName(cryptoFolder, name, SYMLINK_PREFIX);
	}

	@Override
	String encryptFolderName(CryptoFolder cryptoFolder, String name) throws BackendException {
		return encryptName(cryptoFolder, name, DIR_PREFIX);
	}

	@Override
	CryptoFolder move(CryptoFolder source, CryptoFolder target) throws BackendException {
		assertCryptoFolderAlreadyExists(target);
		CryptoFolder result = folder(target.getParent(), target.getName(), cloudContentRepository.move(source.getDirFile(), target.getDirFile()));

		evictFromCache(source);
		evictFromCache(target);
		return result;
	}

	@Override
	CryptoFile move(CryptoFile source, CryptoFile target) throws BackendException {
		assertCryptoFileAlreadyExists(target);
		return file(target, cloudContentRepository.move(source.getCloudFile(), target.getCloudFile()), source.getSize());
	}

	@Override
	void delete(CloudNode node) throws BackendException {
		if (node instanceof CryptoFolder) {
			CryptoFolder cryptoFolder = (CryptoFolder) node;
			List<CryptoFolder> cryptoSubfolders = deepCollectSubfolders(cryptoFolder);
			for (CryptoFolder cryptoSubfolder : cryptoSubfolders) {
				cloudContentRepository.delete(dirIdInfo(cryptoSubfolder).getCloudFolder());
			}
			cloudContentRepository.delete(dirIdInfo(cryptoFolder).getCloudFolder());
			cloudContentRepository.delete(cryptoFolder.getDirFile());
			evictFromCache(cryptoFolder);
		} else if (node instanceof CryptoFile) {
			CryptoFile cryptoFile = (CryptoFile) node;
			cloudContentRepository.delete(cryptoFile.getCloudFile());
		}
	}

	@Override
	String loadDirId(CryptoFolder folder) throws BackendException, EmptyDirFileException {
		if (RootCryptoFolder.isRoot(folder)) {
			return CryptoConstants.ROOT_DIR_ID;
		} else if (cloudContentRepository.exists(folder.getDirFile())) {
			return new String(loadContentsOfDirFile(folder), UTF_8);
		} else {
			return newDirId();
		}
	}

	@Override
	DirIdCache.DirIdInfo createDirIdInfo(CryptoFolder folder) throws BackendException {
		String dirId = loadDirId(folder);
		return dirIdCache.put(folder, createDirIdInfoFor(dirId));
	}

	@Override
	public CryptoFile write(CryptoFile cryptoFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException {
		return writeShortNameFile(cryptoFile, data, progressAware, replace, length);
	}
}
