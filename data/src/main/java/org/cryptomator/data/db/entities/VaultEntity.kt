package org.cryptomator.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Index.Order
import androidx.room.PrimaryKey

@Entity(
	tableName = "VAULT_ENTITY", //
	indices = [Index(name = "IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID", value = ["FOLDER_PATH", "FOLDER_CLOUD_ID"], orders = [Order.ASC, Order.ASC], unique = true)], //
	foreignKeys = [ForeignKey(CloudEntity::class, ["_id"], ["FOLDER_CLOUD_ID"], onDelete = ForeignKey.SET_NULL)]
)
data class VaultEntity constructor(
	@PrimaryKey @ColumnInfo("_id") override val id: Long?,
	@ColumnInfo("FOLDER_CLOUD_ID") val folderCloudId: Long?, //TODO Map to CloudEntity
	@ColumnInfo("FOLDER_PATH") val folderPath: String?,
	@ColumnInfo("FOLDER_NAME") val folderName: String?,
	@ColumnInfo("CLOUD_TYPE") val cloudType: String,
	@ColumnInfo("PASSWORD") val password: String?,
	@ColumnInfo("POSITION") val position: Int?,
	@ColumnInfo("FORMAT") val format: Int?,
	@ColumnInfo("SHORTENING_THRESHOLD") val shorteningThreshold: Int?,
) : DatabaseEntity