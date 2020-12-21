package org.cryptomator.data.cloud.local.storageaccessframework;

import android.net.Uri;

import org.cryptomator.domain.CloudNode;

public interface LocalStorageAccessNode extends CloudNode {

	Uri getUri();

	LocalStorageAccessFolder getParent();

	String getDocumentId();

}
