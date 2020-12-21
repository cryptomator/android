package org.cryptomator.data.cloud.local.storageaccessframework;

import android.net.Uri;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.util.Optional;

import java.util.Date;

import static android.net.Uri.parse;

class LocalStorageAccessFile implements CloudFile, LocalStorageAccessNode {

	private final LocalStorageAccessFolder parent;
	private final String name;
	private final String path;
	private final Optional<Long> size;
	private final Optional<Date> modified;
	private final String documentId;
	private final String documentUri;

	LocalStorageAccessFile(LocalStorageAccessFolder parent, String name, String path, Optional<Long> size, Optional<Date> modified, String documentId, String documentUri) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.size = size;
		this.modified = modified;
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
	public Optional<Long> getSize() {
		return size;
	}

	@Override
	public Optional<Date> getModified() {
		return modified;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		return internalEquals((LocalStorageAccessFile) obj);
	}

	private boolean internalEquals(LocalStorageAccessFile o) {
		return path.equals(o.path);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 56127034;
		hash = hash * prime + path.hashCode();
		return hash;
	}

}
