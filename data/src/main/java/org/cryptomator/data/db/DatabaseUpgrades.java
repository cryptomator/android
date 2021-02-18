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

	@Inject
	public DatabaseUpgrades( //
			Upgrade0To1 upgrade0To1, //
			Upgrade1To2 upgrade1To2, //
			Upgrade2To3 upgrade2To3, //
			Upgrade3To4 upgrade3To4) {

		availableUpgrades = defineUpgrades( //
				upgrade0To1, //
				upgrade1To2, //
				upgrade2To3, //
				upgrade3To4);
	}

	private static Comparator<DatabaseUpgrade> reverseOrder() {
		return (a, b) -> b.compareTo(a);
	}

	private Map<Integer, List<DatabaseUpgrade>> defineUpgrades(DatabaseUpgrade... upgrades) {
		Map<Integer, List<DatabaseUpgrade>> result = new HashMap<>();
		for (DatabaseUpgrade upgrade : upgrades) {
			if (!result.containsKey(upgrade.from())) {
				result.put(upgrade.from(), new ArrayList<>());
			}
			result.get(upgrade.from()).add(upgrade);
		}
		for (List<DatabaseUpgrade> list : result.values()) {
			Collections.sort(list, reverseOrder());
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
