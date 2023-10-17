package org.cryptomator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.cryptomator.data.db.entities.VaultEntity

@Dao
interface VaultDao {

	@Query("SELECT * FROM VAULT_ENTITY WHERE id = :id LIMIT 1")
	fun load(id: Long): VaultEntity

	@Query("SELECT * from VAULT_ENTITY")
	fun loadAll(): List<VaultEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	fun storeReplacing(entity: VaultEntity): RowId

	@Query("SELECT * FROM VAULT_ENTITY WHERE rowid = :rowId")
	fun loadFromRowId(rowId: RowId): VaultEntity

	@Transaction
	fun storeReplacingAndReload(entity: VaultEntity): VaultEntity {
		return loadFromRowId(storeReplacing(entity))
	}

	@Delete
	fun delete(entity: VaultEntity)
}