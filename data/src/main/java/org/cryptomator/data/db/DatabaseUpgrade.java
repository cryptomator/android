package org.cryptomator.data.db;

import org.greenrobot.greendao.database.Database;

import timber.log.Timber;

abstract class DatabaseUpgrade implements Comparable<DatabaseUpgrade> {

	private final int from;
	private final int to;

	DatabaseUpgrade(int from, int to) {
		this.from = from;
		this.to = to;
	}

	public int from() {
		return from;
	}

	public int to() {
		return to;
	}

	@Override
	public int compareTo(DatabaseUpgrade other) {
		int compareByFrom = from - other.from;
		if (compareByFrom != 0) {
			return compareByFrom;
		}
		return to - other.to;
	}

	final void applyTo(Database db, int origin) {
		Timber.tag("DatabaseUpgrade").i("Running %s (%d -> %d)", getClass().getSimpleName(), from, to);
		internalApplyTo(db, origin);
	}

	protected abstract void internalApplyTo(Database db, int origin);

}
