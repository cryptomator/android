package org.cryptomator.domain.repository

import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.exception.BackendException

interface HubRepository {

	/**
	 * Retrieves vault access data for the specified unverified hub vault configuration using the provided access token.
	 *
	 * @param unverifiedHubVaultConfig The unverified hub vault configuration that identifies the vault.
	 * @param accessToken The access token to authenticate the request against the hub.
	 * @return A [VaultAccess] containing the vault key in JWE form and the vault's subscription state.
	 * @throws BackendException If the backend request fails or access is denied.
	 */
	@Throws(BackendException::class)
	fun getVaultAccess(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, accessToken: String): VaultAccess

	/**
	 * Fetches the hub user associated with the given unverified hub vault configuration and access token.
	 *
	 * @param unverifiedHubVaultConfig The unverified hub vault configuration identifying the vault context.
	 * @param accessToken The access token used to authenticate the request against the hub.
	 * @return A UserDto containing the user's id, name, public key, private key, and setup code.
	 * @throws BackendException If the backend request fails or returns an error.
	 */
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

	data class VaultAccess(val vaultKeyJwe: String, val subscriptionState: SubscriptionState)

	enum class SubscriptionState {
		ACTIVE, INACTIVE
	}

}
