package org.cryptomator.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CLOUD_ENTITY")
data class CloudEntity(
	@PrimaryKey override var id: Long?,
	var type: String,
	var accessToken: String? = null,
	var url: String? = null,
	var username: String? = null,
	var webdavCertificate: String? = null,
	var s3Bucket: String? = null,
	var s3Region: String? = null,
	var s3SecretKey: String? = null,
) : DatabaseEntity {

	companion object {

		@JvmStatic
		fun newEntity(id: Long?, name: String): CloudEntity = CloudEntity(id, name)
	}
}