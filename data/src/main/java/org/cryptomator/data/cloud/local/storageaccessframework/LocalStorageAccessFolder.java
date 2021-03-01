package org.cryptomator.data.cloud.local.storageaccessframework;

import android.net.Uri;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

import static android.net.Uri.parse;

class LocalStorageAccessFolder implements CloudFolder, LocalStorageAccessNode {

	private final LocalStorageAccessFolder parent;
	private final String name;
	private final String path;
	private final String documentId;
	private final String documentUri;

	LocalStorageAccessFolder(LocalStorageAccessFolder parent, String name, String path, String documentId, String documentUri) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.documentId = documentId;
		this.documentUri = documentUri;
	}

	@Override
	public Cloud getCloud() {
		return parent.getCloud();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public Uri getUri() {
		if (documentUri == null) {
			return null;
		}

		return parse(documentUri);
	}

	@Override
	public LocalStorageAccessFolder getParent() {
		return parent;
	}

	@Override
	public String getDocumentId() {
		return documentId;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		return internalEquals((LocalStorageAccessFolder) obj);
	}

	private boolean internalEquals(LocalStorageAccessFolder o) {
		return path.equals(o.path);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 341797327;
		hash = hash * prime + path.hashCode();
		return hash;
	}

	@Override
	public LocalStorageAccessFolder withCloud(Cloud cloud) {
		return new LocalStorageAccessFolder(parent.withCloud(cloud), name, path, documentId, documentUri);
	}
}
