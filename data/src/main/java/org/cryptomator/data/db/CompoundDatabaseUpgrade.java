package org.cryptomator.data.db;

import java.util.List;

class CompoundDatabaseUpgrade extends DatabaseUpgrade {

	private final List<DatabaseUpgrade> upgrades;

	public CompoundDatabaseUpgrade(List<DatabaseUpgrade> upgrades) {
		super(upgrades.get(0).from(), upgrades.get(upgrades.size() - 1).to());
		this.upgrades = upgrades;
	}

	@Override
	protected void internalApplyTo(org.greenrobot.greendao.database.Database db, int origin) {
		for (DatabaseUpgrade upgrade : upgrades) {
			upgrade.applyTo(db, origin);
		}
	}

}
