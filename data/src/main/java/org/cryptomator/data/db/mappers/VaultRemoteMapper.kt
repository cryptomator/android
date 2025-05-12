package org.cryptomator.data.db.mappers

import org.cryptomator.data.dto.VaultRemoteDTO
import org.cryptomator.domain.model.VaultRemote
import javax.inject.Singleton

@Singleton

class VaultRemoteMapper() {
    fun toDomain(dto: VaultRemoteDTO): VaultRemote {
        return VaultRemote(
            id = dto.id,
            deviceId = dto.deviceId,
            fullDeviceId = dto.fullDeviceId,
            serial = dto.serial,
            serialBarcode = dto.serialBarcode,
            volumeName = dto.volumeName,
            volumeDescription = dto.volumeDescription,
            owner = dto.owner?.let { owner ->
                VaultRemote.Owner(
                    id = owner.id,
                    name = owner.name,
                    email = owner.email
                )
            },
            version = dto.version,
            machineID = dto.machineID,
            computers = dto.computers?.map { computer ->
                VaultRemote.Computer(
                    hostname = computer.hostname,
                    serial = computer.serial,
                    platform = computer.platform,
                    distro = computer.distro,
                    release = computer.release,
                    build = computer.build,
                    kernel = computer.kernel,
                    codename = computer.codename,
                    arch = computer.arch,
                    volumePath = computer.volumePath,
                    cloudPath = computer.cloudPath,
                    clientVersion = computer.clientVersion,
                    logofile = computer.logofile,
                    fqdn = computer.fqdn,
                    latestUseDate = computer.latestUseDate,
                    vaultStatus = computer.vaultStatus
                )
            },
            status = dto.status,
            lastMpwdUpdated = dto.lastMpwdUpdated,
            lastUserEventDate = dto.lastUserEventDate,
        )
    }

    fun toDto(domain: VaultRemote): VaultRemoteDTO {
        return VaultRemoteDTO(
            id = domain.id,
            deviceId = domain.deviceId,
            fullDeviceId = domain.fullDeviceId,
            serial = domain.serial,
            serialBarcode = domain.serialBarcode,
            volumeName = domain.volumeName,
            volumeDescription = domain.volumeDescription,
            owner = domain.owner?.let { owner ->
                VaultRemoteDTO.OwnerDTO(
                    id = owner.id,
                    name = owner.name,
                    email = owner.email
                )
            },
            version = domain.version,
            machineID = domain.machineID,
            computers = domain.computers?.map { computer ->
                VaultRemoteDTO.ComputerDTO(
                    hostname = computer.hostname,
                    serial = computer.serial,
                    platform = computer.platform,
                    distro = computer.distro,
                    release = computer.release,
                    build = computer.build,
                    kernel = computer.kernel,
                    codename = computer.codename,
                    arch = computer.arch,
                    volumePath = computer.volumePath,
                    cloudPath = computer.cloudPath,
                    clientVersion = computer.clientVersion,
                    logofile = computer.logofile,
                    fqdn = computer.fqdn,
                    latestUseDate = computer.latestUseDate,
                    vaultStatus = computer.vaultStatus
                )
            },
            status = domain.status,
            lastMpwdUpdated = domain.lastMpwdUpdated,
            lastUserEventDate = domain.lastUserEventDate,
        )
    }
} 