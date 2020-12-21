package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.CloudFolder;

interface DirIdCache {

	DirIdInfo get(CryptoFolder folder);

	DirIdInfo put(CryptoFolder folder, DirIdInfo dirIdInfo);

	void evict(CryptoFolder folder);

	void evictSubFoldersOf(CryptoFolder cryptoFolder);

	class DirIdInfo {

		private final String id;
		private final CloudFolder cloudFolder;

		DirIdInfo(String id, CloudFolder cloudFolder) {
			this.id = id;
			this.cloudFolder = cloudFolder;
		}

		public String getId() {
			return id;
		}

		public CloudFolder getCloudFolder() {
			return cloudFolder;
		}

		DirIdInfo withCloudFolder(CloudFolder cloudFolder) {
			return new DirIdInfo(id, cloudFolder);
		}
	}

}
