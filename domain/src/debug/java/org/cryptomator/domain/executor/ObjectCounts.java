package org.cryptomator.domain.executor;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

class ObjectCounts<T> {

	private static final Integer ZERO = 0;

	private final Map<T, Integer> map = new HashMap<>();
	private int count = 0;

	public Integer addAndGet(T value) {
		Integer newValue = count(value) + 1;
		map.put(value, newValue);
		count++;
		return newValue;
	}

	public Integer removeAndGet(T value) {
		Integer newValue = count(value) - 1;
		if (newValue < 0) {
			RuntimeException e = new RuntimeException("Counter of " + value + " below zero");
			e.fillInStackTrace();
			Timber.tag("AsyncExceutionMonitor").e(e);
			newValue = 0;
		}
		map.put(value, newValue);
		count--;
		if (count < 0) {
			RuntimeException e = new RuntimeException("Count below zero");
			e.fillInStackTrace();
			Timber.tag("AsyncExceutionMonitor").e(e);
			count = 0;
		}
		return newValue;
	}

	public Integer count() {
		return count;
	}

	public Integer count(T value) {
		Integer result = map.get(value);
		if (result == null) {
			return ZERO;
		} else {
			return result;
		}
	}

}
