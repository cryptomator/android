package org.cryptomator.domain.repository

import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.BackendException

interface SharepointRepository {

	/**
	 * Document libraries of the cloud's site, each as a copy of the cloud with drive id and name set.
	 * Throws [org.cryptomator.domain.exception.NoSuchCloudFileException] if the site doesn't exist.
	 */
	@Throws(BackendException::class)
	fun drives(cloud: SharepointCloud): List<SharepointCloud>

}
