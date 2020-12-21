package org.cryptomator.generator.model;

import org.cryptomator.generator.utils.Field;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class InstanceStatesModel {

	private final Map<String, InstanceStateModel> instanceStatesByPackage = new HashMap<>();

	public void add(Field field) {
		String packageName = field.declaringType().packageName();
		if (!instanceStatesByPackage.containsKey(packageName)) {
			instanceStatesByPackage.put(packageName, new InstanceStateModel(packageName));
		}
		instanceStatesByPackage.get(packageName).add(field);
	}

	public Stream<InstanceStateModel> instanceStates() {
		return instanceStatesByPackage.values().stream();
	}

}
