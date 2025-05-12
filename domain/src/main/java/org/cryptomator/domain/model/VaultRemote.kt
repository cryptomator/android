package org.cryptomator.domain.model

import java.io.Serializable

data class VaultRemote(
	val id: String?,
	val deviceId: String?,
	val fullDeviceId: String?,
	val serial: String?,
	val serialBarcode: String?,
	val volumeName: String?,
	val volumeDescription: String?,
	val owner: Owner?,
	val version: String?,
	val machineID: String?,
	val computers: List<Computer>?,
	val status: String?,
	val lastMpwdUpdated: Long?,
	val lastUserEventDate: Long?
) : Serializable {

	data class Owner(
		val id: String?,
		val name: String?,
		val email: String?
	) : Serializable

	data class Computer(
		val hostname: String?,
		val serial: String?,
		val platform: String?,
		val distro: String?,
		val release: String?,
		val build: String?,
		val kernel: String?,
		val codename: String?,
		val arch: String?,
		val volumePath: String?,
		val cloudPath: String?,
		val clientVersion: String?,
		val logofile: String?,
		val fqdn: String?,
		val latestUseDate: Long?,
		val vaultStatus: String?
	) : Serializable
} 