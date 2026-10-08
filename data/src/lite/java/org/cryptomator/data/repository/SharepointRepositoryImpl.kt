package org.cryptomator.data.repository

import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.SharepointRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharepointRepositoryImpl @Inject constructor() : SharepointRepository {

	override fun drives(cloud: SharepointCloud): List<SharepointCloud> {
		throw FatalBackendException("SharePoint is not supported in the lite flavor")
	}
}
