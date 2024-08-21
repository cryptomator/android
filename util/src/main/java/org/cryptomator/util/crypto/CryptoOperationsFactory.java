package org.cryptomator.util.crypto;

class CryptoOperationsFactory {

	private static volatile CryptoOperations cryptoOperationsCBC;
	private static volatile CryptoOperations cryptoOperationsGCM;

	public static CryptoOperations cryptoOperations(CryptoMode mode) {
		return switch (mode) {
			case CBC -> {
				if (cryptoOperationsCBC == null) {
					synchronized (CryptoOperations.class) {
						if (cryptoOperationsCBC == null) {
							cryptoOperationsCBC = new CryptoOperationsCBC();
						}
					}
				}
				yield cryptoOperationsCBC;
			}
			case GCM -> {
				if (cryptoOperationsGCM == null) {
					synchronized (CryptoOperations.class) {
						if (cryptoOperationsGCM == null) {
							cryptoOperationsGCM = new CryptoOperationsGCM();
						}
					}
				}
				yield cryptoOperationsGCM;
			}
			case NONE -> throw new IllegalArgumentException("CryptoMode.NONE is not allowed here");
		};
	}
}
