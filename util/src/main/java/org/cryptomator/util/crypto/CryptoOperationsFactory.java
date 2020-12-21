package org.cryptomator.util.crypto;

class CryptoOperationsFactory {

	private static volatile CryptoOperations cryptoOperations;

	public static CryptoOperations cryptoOperations() {
		if (cryptoOperations == null) {
			synchronized (CryptoOperations.class) {
				if (cryptoOperations == null) {
					cryptoOperations = createCryptoOperations();
				}
			}
		}
		return cryptoOperations;
	}

	private static CryptoOperations createCryptoOperations() {
		return new CryptoOperationsFromApi23();
	}

}
