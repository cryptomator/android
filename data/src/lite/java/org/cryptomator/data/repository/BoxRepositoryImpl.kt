package org.cryptomator.data.repository

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.BoxRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoxRepositoryImpl @Inject constructor() : BoxRepository {

	override fun authenticate(authorizationCode: String): BoxCloud {
		throw FatalBackendException("Box is not supported in the lite flavor")
	}

	override fun reauthenticate(cloud: BoxCloud, authorizationCode: String): BoxCloud {
		throw FatalBackendException("Box is not supported in the lite flavor")
	}
}
