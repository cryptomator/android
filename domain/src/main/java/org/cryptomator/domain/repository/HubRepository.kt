package org.cryptomator.domain.repository

import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.exception.BackendException

interface HubRepository {

	@Throws(BackendException::class)
	fun getVaultKeyJwe(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String): String

	@Throws(BackendException::class)
	fun getUser(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String): UserDto

	@Throws(BackendException::class)
	fun getDevice(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String): DeviceDto

	@Throws(BackendException::class)
	fun createDevice(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String, deviceName: String, setupCode: String, userPrivateKey: String)

	@Throws(BackendException::class)
	fun getConfig(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String): ConfigDto

	data class DeviceDto(val userPrivateKey: String)

	data class ConfigDto(val apiLevel: Int)

	data class UserDto(val id: String, val name: String, val publicKey: String, val privateKey: String, val setupCode: String)

}
