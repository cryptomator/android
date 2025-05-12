package org.cryptomator.data.util

import org.cryptomator.data.dto.DeploymentInfoDTO
import org.cryptomator.data.dto.VaultInfoDTO
import org.cryptomator.data.dto.VaultRemoteDTO

val mockVaultInfo = VaultInfoDTO().apply {
	lastVaultUpdated = 1746849963
	vaultMK = "ewogICJwcmltYXJ5TWFzdGVyS2V5IjogIm5wMGg0b3YrSGUza01pOEN4TCtyM1FBWmVaWWxMZHNBZzd6Tld6TFRWbWl6dkU2c2MvM3JyQT09IiwKICAiaG1hY01hc3RlcktleSI6ICJRVkdKQmpkeWxXZjRlSjR1TFBOTDM0MnZLN1VTUFJJL1A4Uld6dVRiaTFlTmpZajBKMTljMkE9PSIsCiAgInNjcnlwdEJsb2NrU2l6ZSI6IDgsCiAgInNjcnlwdENvc3RQYXJhbSI6IDMyNzY4LAogICJzY3J5cHRTYWx0IjogIm03eEV1anhqZ3A4PSIsCiAgInZlcnNpb24iOiA5OTksCiAgInZlcnNpb25NYWMiOiAiNHVScGpMa1pkWjg3WGl1T2NzaGlKdWcwSUhvYkpPa2FpUkxoWUlKcUgxWT0iCn0="
	vaultData = "eyJraWQiOiJtYXN0ZXJrZXlmaWxlOm1hc3RlcmtleS5jcnlwdG9tYXRvciIsImFsZyI6IkhTMjU2IiwidHlwIjoiSldUIn0.eyJqdGkiOiJhYTdkNGMyNC1iZmQzLTQzOWUtODg1MC0zYmZkOGY4OThkMTkiLCJmb3JtYXQiOjgsImNpcGhlckNvbWJvIjoiU0lWX0dDTSIsInNob3J0ZW5pbmdUaHJlc2hvbGQiOjIyMH0.DKzsARLeEpQ0E8jC_oys7BgfrK6rAdBvWmsj4szzk5U"
	vaultPKI = "eyJraWQiOiJtYXN0ZXJrZXlmaWxlOm1hc3RlcmtleS5jcnlwdG9tYXRvciIsImFsZyI6IkhTMjU2IiwidHlwIjoiSldUIn0.eyJqdGkiOiJhYTdkNGMyNC1iZmQzLTQzOWUtODg1MC0zYmZkOGY4OThkMTkiLCJmb3JtYXQiOjgsImNpcGhlckNvbWJvIjoiU0lWX0dDTSIsInNob3J0ZW5pbmdUaHJlc2hvbGQiOjIyMH0.DKzsARLeEpQ0E8jC_oys7BgfrK6rAdBvWmsj4szzk5U"
}



// Creating a mock OwnerDTO for VaultRemoteDTO
val mockOwner = VaultRemoteDTO.OwnerDTO(
	id = "b40e743f-6547-4c4d-8a3f-834458093380",
	name = "b40e743f-6547-4c4d-8a3f-834458093380",
	email = "rbtesttk@gmail.com"
)

// Creating mock ComputerDTO objects
val mockComputers = listOf(
	VaultRemoteDTO.ComputerDTO(
		hostname = "HuuADR001",
		serial = "9bfcc910-04de-3fec-8d6d-ca1d5d5e6ce3",
		platform = "android",
		distro = "Android 16",
		release = "16",
		build = "BP22.250325.006",
		kernel = "6.6.66-android15-8-gb66429556fb8-ab13070261-4k",
		codename = "Unknown",
		arch = "arm64",
		volumePath = "Upwork-sample",
		cloudPath = "file://Upwork-sample",
		clientVersion = "1.12.0",
		logofile = "android",
		fqdn = "android-9bfcc910.local",
		latestUseDate = 1746849964272,
		vaultStatus = null
	),
	VaultRemoteDTO.ComputerDTO(
		hostname = null,
		serial = "03921cc4-363c-3143-b1f8-4ca1687709db",
		platform = null,
		distro = null,
		release = null,
		build = null,
		kernel = null,
		codename = null,
		arch = null,
		volumePath = null,
		cloudPath = null,
		clientVersion = null,
		logofile = null,
		fqdn = null,
		latestUseDate = null,
		vaultStatus = "ADD_PENDING"
	)
)

// Creating the complete VaultRemoteDTO
val mockVaultRemote = VaultRemoteDTO(
	id = null, // Not specified in the paste.txt
	deviceId = "558794d5-3a95-4d38-9944-a0f4bbc5c238",
	fullDeviceId = "NCRYPTOR.dadr6331e74ee7641f5ee02559/558794d5-3a95-4d38-9944-a0f4bbc5c238",
	serial = "dadr6331e74ee7641f5ee02559",
	serialBarcode = "dadr6331e74ee7641f5ee02559",
	volumeName = "math",
	volumeDescription = "math",
	owner = mockOwner,
	version = "1.12.0",
	machineID = "03921cc4-363c-3143-b1f8-4ca1687709db",
	computers = mockComputers,
	status = "IN_USE",
	lastMpwdUpdated = 1746849963,
	lastUserEventDate = 1746850130159
)

// Creating a complete DeploymentInfoDTO
val mockDeploymentInfo = DeploymentInfoDTO(
	vaultRemote = mockVaultRemote,
	vaultInfo = mockVaultInfo
)

class Mock{
	companion object {
		val vaultInfo = mockVaultInfo
		val vaultRemote = mockVaultRemote
		val deploymentInfo = mockDeploymentInfo
	}
	fun getVaultInfo(): VaultInfoDTO {
		return vaultInfo
	}
	fun getVaultRemote(): VaultRemoteDTO {
		return vaultRemote
	}
	fun getDeploymentInfo(): DeploymentInfoDTO {
		return deploymentInfo
	}
}