package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import org.cryptomator.data.db.entities.VaultEntity;
import org.cryptomator.data.db.entities.VaultWithCloud;

import java.util.List;

@Dao
public interface VaultDao {

	@Transaction
	@Query("SELECT * FROM VAULT_ENTITY")
	List<VaultWithCloud> loadAll();

	@Transaction
	@Query("SELECT * FROM VAULT_ENTITY WHERE _id = :id")
	VaultWithCloud load(Long id);

	@Insert
	long insert(VaultEntity entity);

	@Update
	void update(VaultEntity entity);

	@Delete
	void delete(VaultEntity entity);

	@Transaction
	default VaultWithCloud store(VaultWithCloud entity) {
		VaultEntity vault = entity.getVault();
		Long id = vault.getId();
		if (id == null) {
			id = insert(vault);
		} else {
			update(vault);
		}
		return load(id);
	}

	@Transaction
	default void delete(VaultWithCloud entity) {
		delete(entity.getVault());
	}
}
