package org.cryptomator.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UPDATE_CHECK_ENTITY")
data class UpdateCheckEntity(
	@PrimaryKey @ColumnInfo("_id") override var id: Long?,
	@ColumnInfo("LICENSE_TOKEN") var licenseToken: String?,
	@ColumnInfo("RELEASE_NOTE") var releaseNote: String?,
	@ColumnInfo("VERSION") var version: String?,
	@ColumnInfo("URL_TO_APK") var urlToApk: String?,
	@ColumnInfo("APK_SHA256") var apkSha256: String?,
	@ColumnInfo("URL_TO_RELEASE_NOTE") var urlToReleaseNote: String?,
) : DatabaseEntity