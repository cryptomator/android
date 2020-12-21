package org.cryptomator.domain.executor;

public class BackgroundTasks {

	public static class Registration {
		public void unregister() {
			// empty in production code, only used in debug / test variant
		}
	}

	public static Registration register(Class<?> type) {
		// empty in production code, only used in debug / test variant
		return new Registration();
	}
}
