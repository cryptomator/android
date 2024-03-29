package org.cryptomator.data.db

import org.cryptomator.domain.CloudType
import org.cryptomator.util.SharedPreferencesHandler
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class Upgrade9To10 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(9, 10) {

	private val defaultLocalStorageCloudId = 4L

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()

		try {
			Sql.query("VAULT_ENTITY")
				.columns(listOf("FOLDER_PATH"))
				.where("FOLDER_CLOUD_ID", Sql.eq(defaultLocalStorageCloudId))
				.executeOn(db).use {
					val vaultsToBeRemoved = ArrayList<String>()
					while (it.moveToNext()) {
						val folderPath = it.getString(it.getColumnIndex("FOLDER_PATH"))
						vaultsToBeRemoved.add(folderPath)
					}
					if (vaultsToBeRemoved.isNotEmpty()) {
						sharedPreferencesHandler.vaultsRemovedDuringMigration(Pair(CloudType.LOCAL.name, vaultsToBeRemoved))
						Timber.tag("Upgrade9To10").i("Added %s to the removeDuringMigrations", vaultsToBeRemoved)
					}
				}

			Sql.deleteFrom("VAULT_ENTITY")
				.where("FOLDER_CLOUD_ID", Sql.eq(defaultLocalStorageCloudId))
				.executeOn(db)

			Sql.deleteFrom("CLOUD_ENTITY")
				.where("_id", Sql.eq(defaultLocalStorageCloudId))
				.where("TYPE", Sql.eq("LOCAL"))
				.executeOn(db)

			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}
}
