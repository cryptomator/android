package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.Supplier;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

import static java.lang.String.format;

class CryptoCloudContentRepository implements CloudContentRepository<CryptoCloud, CryptoNode, CryptoFolder, CryptoFile> {

	private final CryptoImplDecorator cryptoImpl;

	CryptoCloudContentRepository(Context context, CloudContentRepository cloudContentRepository, CryptoCloud cloud, Supplier<Cryptor> cryptor) {
		CloudFolder vaultLocation;
		try {
			vaultLocation = cloudContentRepository.resolve(cloud.getVault().getCloud(), cloud.getVault().getPath());
		} catch (BackendException e) {
			throw new FatalBackendException(e);
		}

		switch (cloud.getVault().getVersion()) {
			case 7:
				this.cryptoImpl = new CryptoImplVaultFormat7(context, cryptor, cloudContentRepository, vaultLocation, new DirIdCacheFormat7());
				break;
			case 6:
			case 5:
				this.cryptoImpl = new CryptoImplVaultFormatPre7(context, cryptor, cloudContentRepository, vaultLocation, new DirIdCacheFormatPre7());
				break;
			default:
				throw new IllegalStateException(format("No CryptoImpl for vault version %d.", cloud.getVault().getVersion()));
		}
	}

	@Override
	public synchronized CryptoFolder root(CryptoCloud cloud) throws BackendException {
		return cryptoImpl.root(cloud);
	}

	@Override
	public CryptoFolder resolve(CryptoCloud cloud, String path) throws BackendException {
		return cryptoImpl.resolve(cloud, path);
	}

	@Override
	public CryptoFile file(CryptoFolder parent, String name) throws BackendException {
		return cryptoImpl.file(parent, name);
	}

	@Override
	public CryptoFile file(CryptoFolder parent, String name, Optional<Long> size) throws BackendException {
		return cryptoImpl.file(parent, name, size);
	}

	@Override
	public CryptoFolder folder(CryptoFolder parent, String name) throws BackendException {
		return cryptoImpl.folder(parent, name);
	}

	@Override
	public boolean exists(CryptoNode node) throws BackendException {
		return cryptoImpl.exists(node);
	}

	@Override
	public List<CryptoNode> list(CryptoFolder folder) throws BackendException {
		return cryptoImpl.list(folder);
	}

	@Override
	public CryptoFolder create(CryptoFolder folder) throws BackendException {
		try {
			return cryptoImpl.create(folder);
		} catch (CloudNodeAlreadyExistsException e) {
			throw new CloudNodeAlreadyExistsException(folder.getName());
		}
	}

	@Override
	public CryptoFolder move(CryptoFolder source, CryptoFolder target) throws BackendException {
		try {
			return cryptoImpl.move(source, target);
		} catch (CloudNodeAlreadyExistsException e) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}
	}

	@Override
	public CryptoFile move(CryptoFile source, CryptoFile target) throws BackendException {
		try {
			return cryptoImpl.move(source, target);
		} catch (CloudNodeAlreadyExistsException e) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}
	}

	@Override
	public CryptoFile write(CryptoFile file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long length) throws BackendException {
		return cryptoImpl.write(file, data, progressAware, replace, length);
	}

	@Override
	public void read(CryptoFile file, Optional<File> tmpEncryptedFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		cryptoImpl.read(file, data, progressAware);
	}

	@Override
	public void delete(CryptoNode node) throws BackendException {
		cryptoImpl.delete(node);
	}

	@Override
	public String checkAuthenticationAndRetrieveCurrentAccount(CryptoCloud cloud) throws BackendException {
		return cryptoImpl.currentAccount(cloud);
	}

	@Override
	public void logout(CryptoCloud cloud) throws BackendException {
		// empty
	}
}
