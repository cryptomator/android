package org.cryptomator.util.crypto;

class CryptoOperationsFactory {

	private static volatile CryptoOperations cryptoOperationsCBC;
	private static volatile CryptoOperations cryptoOperationsGCM;

	public static CryptoOperations cryptoOperations(CryptoMode mode) {
		if (mode == CryptoMode.CBC) {
			if (cryptoOperationsCBC == null) {
				synchronized (CryptoOperations.class) {
					if (cryptoOperationsCBC == null) {
						cryptoOperationsCBC = new CryptoOperationsCBC();
					}
				}
			}
			return cryptoOperationsCBC;
		} else {
			if (cryptoOperationsGCM == null) {
				synchronized (CryptoOperations.class) {
					if (cryptoOperationsGCM == null) {
						cryptoOperationsGCM = new CryptoOperationsGCM();
					}
				}
			}
			return cryptoOperationsGCM;
		}
	}
}
