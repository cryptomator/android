package org.cryptomator.domain.repository;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

/**
 * <p>
 * An interface to retrieve the contents of a cloud.
 * <p>
 * A CloudContentRepository will throw {@link AuthenticationException AuthenticationExceptions}
 * from any operation if AuthenticationExceptions occur to allow correct handling in the UI.
 */
public interface CloudContentRepository<CloudType extends Cloud, NodeType extends CloudNode, DirType extends CloudFolder, FileType extends CloudFile> {

	DirType root(CloudType cloud) throws BackendException;

	DirType resolve(CloudType cloud, String path) throws BackendException;

	FileType file(DirType parent, String name) throws BackendException;

	FileType file(DirType parent, String name, Optional<Long> size) throws BackendException;

	DirType folder(DirType parent, String name) throws BackendException;

	boolean exists(NodeType node) throws BackendException;

	List<? extends CloudNode> list(DirType folder) throws BackendException;

	/**
	 * Creates a cloud folder and maybe intermediate directories.
	 *
	 * @return created cloud folder (migth be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same folder name already exists
	 */
	DirType create(DirType folder) throws BackendException;

	/**
	 * @return moved cloud folder (might be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same target name already exists
	 */
	DirType move(DirType source, DirType target) throws BackendException;

	/**
	 * @return moved cloud file (might be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same target name already exists
	 */
	FileType move(FileType source, FileType target) throws BackendException;

	/**
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same file name already exists
	 */
	FileType write(FileType file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException;

	void read(FileType file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException;

	void delete(NodeType node) throws BackendException;

	String checkAuthenticationAndRetrieveCurrentAccount(CloudType cloud) throws BackendException;

	/**
	 * Performs a logout. After a call to this method further usage of this cloud will cause {@link org.cryptomator.cryptolib.api.AuthenticationFailedException AuthenticationFailedExceptions}.
	 */
	void logout(CloudType cloud) throws BackendException;
}
