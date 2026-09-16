package org.cryptomator.data.db;

import androidx.room.migration.Migration;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class DatabaseUpgrades {

	private final Migration[] upgrades;

	@Inject
	public DatabaseUpgrades( //
			Upgrade1To2 upgrade1To2, //
			Upgrade2To3 upgrade2To3, //
			Upgrade3To4 upgrade3To4, //
			Upgrade4To5 upgrade4To5, //
			Upgrade5To6 upgrade5To6, //
			Upgrade6To7 upgrade6To7, //
			Upgrade7To8 upgrade7To8, //
			Upgrade8To9 upgrade8To9, //
			Upgrade9To10 upgrade9To10, //
			Upgrade10To11 upgrade10To11, //
			Upgrade11To12 upgrade11To12, //
			Upgrade12To13 upgrade12To13, //
			Upgrade13To14 upgrade13To14 //
	) {
		upgrades = new Migration[] { //
				upgrade1To2, //
				upgrade2To3, //
				upgrade3To4, //
				upgrade4To5, //
				upgrade5To6, //
				upgrade6To7, //
				upgrade7To8, //
				upgrade8To9, //
				upgrade9To10, //
				upgrade10To11, //
				upgrade11To12, //
				upgrade12To13, //
				upgrade13To14};
	}

	public Migration[] all() {
		return upgrades.clone();
	}
}
