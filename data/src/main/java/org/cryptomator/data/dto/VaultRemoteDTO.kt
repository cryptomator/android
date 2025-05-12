package org.cryptomator.data.dto

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class VaultRemoteDTO(
	val id: String? = null,

	@SerializedName("device_id")
	val deviceId: String? = null,

	@SerializedName("deviceId")
	val fullDeviceId: String? = null,

	val serial: String? = null,

	@SerializedName("serialBarcode")
	val serialBarcode: String? = null,

	@SerializedName("volumeName")
	val volumeName: String? = null,

	@SerializedName("volumeDescription")
	val volumeDescription: String? = null,

	val owner: OwnerDTO? = null,

	val version: String? = null,

	@SerializedName("machineID")
	val machineID: String? = null,

	val computers: List<ComputerDTO>? = null,

	val status: String? = null,

	@SerializedName("lastMpwdUpdated")
	val lastMpwdUpdated: Long? = null,

	@SerializedName("lastUserEventDate")
	val lastUserEventDate: Long? = null
) : Serializable {


	data class OwnerDTO(
		val id: String? = null,
		val name: String? = null,
		val email: String? = null
	) : Serializable

	data class ComputerDTO(
		val hostname: String? = null,
		val serial: String? = null,
		val platform: String? = null,
		val distro: String? = null,
		val release: String? = null,
		val build: String? = null,
		val kernel: String? = null,
		val codename: String? = null,
		val arch: String? = null,
		val volumePath: String? = null,
		val cloudPath: String? = null,
		val clientVersion: String? = null,
		val logofile: String? = null,
		val fqdn: String? = null,
		val latestUseDate: Long? = null,
		val vaultStatus: String? = null
	) : Serializable
}