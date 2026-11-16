package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import org.cryptomator.data.db.entities.CloudEntity;

import java.util.List;

@Dao
public interface CloudDao {

	@Query("SELECT * FROM CLOUD_ENTITY")
	List<CloudEntity> loadAll();

	@Query("SELECT * FROM CLOUD_ENTITY WHERE _id = :id")
	CloudEntity load(Long id);

	@Insert
	long insert(CloudEntity entity);

	@Update
	void update(CloudEntity entity);

	@Query("UPDATE CLOUD_ENTITY SET ACCESS_TOKEN = :accessToken WHERE _id = :id")
	void updateAccessToken(long id, String accessToken);

	@Delete
	void delete(CloudEntity entity);

	@Transaction
	default CloudEntity store(CloudEntity entity) {
		Long id = entity.getId();
		if (id == null) {
			id = insert(entity);
		} else {
			update(entity);
		}
		return load(id);
	}

	/**
	 * Like {@link #store(CloudEntity)} but keeps the stored access token, for clouds whose tokens are written by {@link #updateAccessToken(long, String)} only.
	 */
	@Transaction
	default CloudEntity storeKeepingAccessToken(CloudEntity entity) {
		CloudEntity storedEntity = entity.getId() == null ? null : load(entity.getId());
		entity.setAccessToken(storedEntity == null ? null : storedEntity.getAccessToken());
		return store(entity);
	}
}
