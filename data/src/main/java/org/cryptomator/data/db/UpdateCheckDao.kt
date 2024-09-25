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

internal class DelegatingUpdateCheckDao(private val database: Invalidatable<CryptomatorDatabase>) : UpdateCheckDao {

	private val delegate: UpdateCheckDao
		get() = database.call().updateCheckDao()

	override fun load(id: Long): UpdateCheckEntity = delegate.load(id)

	override fun loadAll(): List<UpdateCheckEntity> = delegate.loadAll()

	override fun storeReplacing(entity: UpdateCheckEntity): RowId = delegate.storeReplacing(entity)

	override fun loadFromRowId(rowId: RowId): UpdateCheckEntity = delegate.loadFromRowId(rowId)

	override fun storeReplacingAndReload(entity: UpdateCheckEntity): UpdateCheckEntity = delegate.storeReplacingAndReload(entity)

	override fun delete(entity: UpdateCheckEntity) = delegate.delete(entity)
}