package org.cryptomator.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UPDATE_CHECK_ENTITY")
data class UpdateCheckEntity @JvmOverloads constructor(
	//TODO Remove @JvmOverloads
	@PrimaryKey override var id: Long?,
	var licenseToken: String?,
	var releaseNote: String?,
	var version: String?,
	var urlToApk: String?,
	var apkSha256: String?,
	var urlToReleaseNote: String?,
) : DatabaseEntity