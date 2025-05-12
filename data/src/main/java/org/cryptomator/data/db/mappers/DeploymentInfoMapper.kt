package org.cryptomator.data.db.mappers

import org.cryptomator.data.dto.DeploymentInfoDTO
import org.cryptomator.data.dto.VaultInfoDTO
import org.cryptomator.domain.model.DeploymentInfo
import org.cryptomator.domain.model.VaultInfo
import javax.inject.Singleton

@Singleton
class DeploymentInfoMapper(
    private val vaultRemoteMapper: VaultRemoteMapper
) {
    fun toDomain(dto: DeploymentInfoDTO): DeploymentInfo {
        return DeploymentInfo(
            vaultRemote = vaultRemoteMapper.toDomain(dto.vaultRemote),
            vaultInfo = toVaultInfo(dto.vaultInfo)
        )
    }

    fun toDto(domain: DeploymentInfo): DeploymentInfoDTO {
        return DeploymentInfoDTO(
            vaultRemote = vaultRemoteMapper.toDto(domain.vaultRemote),
            vaultInfo = toVaultInfoDTO(domain.vaultInfo)
        )
    }

    private fun toVaultInfo(dto: VaultInfoDTO): VaultInfo {
        return VaultInfo(
            lastVaultUpdated = dto.lastVaultUpdated,
            vaultMK = dto.vaultMK ?: "",
            vaultData = dto.vaultData ?: "",
            vaultPKI = dto.vaultPKI ?: ""
        )
    }

    private fun toVaultInfoDTO(domain: VaultInfo): VaultInfoDTO {
        return VaultInfoDTO().apply {
            lastVaultUpdated = domain.lastVaultUpdated
            vaultMK = domain.vaultMK
            vaultData = domain.vaultData
            vaultPKI = domain.vaultPKI
        }
    }
} 