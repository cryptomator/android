package org.cryptomator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.cryptomator.data.db.entities.UpdateCheckEntity

@Dao
interface UpdateCheckDao {

	@Query("SELECT * FROM UPDATE_CHECK_ENTITY WHERE id = :id LIMIT 1")
	fun load(id: Long): UpdateCheckEntity

	@Query("SELECT * from UPDATE_CHECK_ENTITY")
	fun loadAll(): List<UpdateCheckEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun storeReplacing(entity: UpdateCheckEntity): RowId

	@Query("SELECT * FROM UPDATE_CHECK_ENTITY WHERE rowid = :rowId")
	fun loadFromRowId(rowId: RowId): UpdateCheckEntity

	@Transaction
	fun storeReplacingAndReload(entity: UpdateCheckEntity): UpdateCheckEntity {
		return loadFromRowId(storeReplacing(entity))
	}

	@Delete
	fun delete(entity: UpdateCheckEntity)

}