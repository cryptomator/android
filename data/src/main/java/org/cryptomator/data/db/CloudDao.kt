package org.cryptomator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.VaultEntity

@Dao
interface CloudDao {

	@Query("SELECT * FROM CLOUD_ENTITY WHERE id = :id LIMIT 1")
	fun load(id: Long): CloudEntity

	@Query("SELECT * from CLOUD_ENTITY")
	fun loadAll(): List<CloudEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun storeReplacing(entity: CloudEntity): RowId

	@Query("SELECT * FROM CLOUD_ENTITY WHERE rowid = :rowId")
	fun loadFromRowId(rowId: RowId): CloudEntity

	@Transaction
	fun storeReplacingAndReload(entity: CloudEntity): CloudEntity {
		return loadFromRowId(storeReplacing(entity))
	}

	@Delete
	fun delete(entity: CloudEntity)
}