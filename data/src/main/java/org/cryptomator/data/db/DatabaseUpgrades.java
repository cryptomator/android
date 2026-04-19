package org.cryptomator.data.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.lang.String.format;

@Singleton
class DatabaseUpgrades {

	private final Map<Integer, List<DatabaseUpgrade>> availableUpgrades;

	/**
	 * Creates the DatabaseUpgrades registry and registers the provided upgrade steps
	 * into the internal mapping keyed by each step's source version.
	 *
	 * Each constructor parameter is the implementation for the corresponding
	 * from->to version and will be added to the availableUpgrades map.
	 *
	 * @param upgrade0To1  upgrade step from version 0 to 1
	 * @param upgrade1To2  upgrade step from version 1 to 2
	 * @param upgrade2To3  upgrade step from version 2 to 3
	 * @param upgrade3To4  upgrade step from version 3 to 4
	 * @param upgrade4To5  upgrade step from version 4 to 5
	 * @param upgrade5To6  upgrade step from version 5 to 6
	 * @param upgrade6To7  upgrade step from version 6 to 7
	 * @param upgrade7To8  upgrade step from version 7 to 8
	 * @param upgrade8To9  upgrade step from version 8 to 9
	 * @param upgrade9To10 upgrade step from version 9 to 10
	 * @param upgrade10To11 upgrade step from version 10 to 11
	 * @param upgrade11To12 upgrade step from version 11 to 12
	 * @param upgrade12To13 upgrade step from version 12 to 13
	 * @param upgrade13To14 upgrade step from version 13 to 14
	 */
	@Inject
	public DatabaseUpgrades( //
			Upgrade0To1 upgrade0To1, //
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
			Upgrade13To14 upgrade13To14
	) {

		availableUpgrades = defineUpgrades( //
				upgrade0To1, //
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
				upgrade13To14);
	}

	/**
	 * Builds a registry that groups provided upgrades by their source version.
	 *
	 * @param upgrades varargs of available DatabaseUpgrade instances to register
	 * @return a map from source version to the list of upgrades that start at that version; each list is sorted in descending (reverse natural) order
	 */
	private Map<Integer, List<DatabaseUpgrade>> defineUpgrades(DatabaseUpgrade... upgrades) {
		Map<Integer, List<DatabaseUpgrade>> result = new HashMap<>();
		for (DatabaseUpgrade upgrade : upgrades) {
			if (!result.containsKey(upgrade.from())) {
				result.put(upgrade.from(), new ArrayList<>());
			}
			result.get(upgrade.from()).add(upgrade);
		}
		for (List<DatabaseUpgrade> list : result.values()) {
			Collections.sort(list,  Comparator.reverseOrder());
		}
		return result;
	}

	public DatabaseUpgrade getUpgrade(int oldVersion, int newVersion) {
		List<DatabaseUpgrade> upgrades = new ArrayList<>(10);
		if (!findUpgrades(upgrades, oldVersion, newVersion)) {
			throw new IllegalStateException(format("No upgrade path from %d to %d", oldVersion, newVersion));
		}
		return new CompoundDatabaseUpgrade(upgrades);
	}

	private boolean findUpgrades(List<DatabaseUpgrade> upgrades, int oldVersion, int newVersion) {
		if (oldVersion == newVersion) {
			return true;
		}

		List<DatabaseUpgrade> upgradesFromOldVersion = availableUpgrades.get(oldVersion);
		if (upgradesFromOldVersion == null) {
			return false;
		}
		for (DatabaseUpgrade upgrade : upgradesFromOldVersion) {
			if (upgrade.to() > newVersion) {
				continue;
			}
			upgrades.add(upgrade);
			if (findUpgrades(upgrades, upgrade.to(), newVersion)) {
				return true;
			}
			upgrades.remove(upgrades.size() - 1);
		}
		return false;
	}
}
