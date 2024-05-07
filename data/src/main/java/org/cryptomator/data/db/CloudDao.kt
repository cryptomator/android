package org.cryptomator.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.cryptomator.data.db.entities.CloudEntity

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

internal class DelegatingCloudDao(private val database: Invalidatable<CryptomatorDatabase>) : CloudDao {

	private val delegate: CloudDao
		get() = database.call().cloudDao()

	override fun load(id: Long): CloudEntity = delegate.load(id)

	override fun loadAll(): List<CloudEntity> = delegate.loadAll()

	override fun storeReplacing(entity: CloudEntity): RowId = delegate.storeReplacing(entity)

	override fun loadFromRowId(rowId: RowId): CloudEntity = delegate.loadFromRowId(rowId)

	override fun storeReplacingAndReload(entity: CloudEntity): CloudEntity = delegate.storeReplacingAndReload(entity)

	override fun delete(entity: CloudEntity) = delegate.delete(entity)
}