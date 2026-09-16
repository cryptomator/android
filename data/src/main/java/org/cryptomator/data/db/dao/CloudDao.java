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
}
