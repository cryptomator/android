package org.cryptomator.data.db;

import org.cryptomator.data.db.entities.DaoMaster;
import org.cryptomator.data.db.entities.DaoSession;
import org.cryptomator.data.db.entities.DatabaseEntity;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Database {

	private final DaoSession daoSession;

	@Inject
	public Database(DatabaseFactory databaseFactory) {
		DaoMaster daoMaster = new DaoMaster(databaseFactory.getWritableDatabase());
		daoSession = daoMaster.newSession();
	}

	public <T extends DatabaseEntity> T load(Class<T> type, long id) {
		return daoSession.load(type, id);
	}

	public <T extends DatabaseEntity> void delete(T entity) {
		daoSession.delete(entity);
	}

	public <T extends DatabaseEntity> List<T> loadAll(Class<T> type) {
		return daoSession.loadAll(type);
	}

	public <T extends DatabaseEntity> T create(T entity) {
		long id = daoSession.insert(entity);
		return load((Class<T>) entity.getClass(), id);
	}

	public <T extends DatabaseEntity> T store(T entity) {
		Long id = entity.getId();
		if (id == null) {
			id = daoSession.insert(entity);
		} else {
			daoSession.update(entity);
		}
		return load((Class<T>) entity.getClass(), id);
	}

	public void clearCache() {
		daoSession.clear();
	}
}
