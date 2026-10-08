package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Update;

import org.cryptomator.data.db.entities.UpdateCheckEntity;

@Dao
public interface UpdateCheckDao {

	@Query("SELECT * FROM UPDATE_CHECK_ENTITY WHERE _id = :id")
	UpdateCheckEntity load(Long id);

	@Update
	void update(UpdateCheckEntity entity);
}
