package org.cryptomator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "VAULT_ENTITY", //
	indices = [Index(name = "IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID", value = ["folderPath", "folderCloudId"], unique = true)], //
	foreignKeys = [ForeignKey(CloudEntity::class, ["id"], ["folderCloudId"], onDelete = ForeignKey.SET_NULL)]
)
data class VaultEntity constructor(
	@PrimaryKey override val id: Long?,
	val folderCloudId: Long?, //TODO Map to CloudEntity
	val folderPath: String?,
	val folderName: String?,
	val cloudType: String,
	val password: String?,
	val position: Int?,
	val format: Int?,
	val shorteningThreshold: Int?,
) : DatabaseEntity