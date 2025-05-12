package org.cryptomator.domain.repository

import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.model.DeploymentInfo

interface DeploymentRepository {

	@Throws(BackendException::class)
	fun getDeploymentInfo(): List<DeploymentInfo>
}