package org.cryptomator.domain.model

data class Deployment(
	val lastModified: Long,
	val deviceId: String,
	val status: String
) {

	fun shouldGetDeployment(): Boolean {
		return status == "ADD_PENDING"
	}
}