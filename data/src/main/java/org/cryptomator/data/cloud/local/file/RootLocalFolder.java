package org.cryptomator.data.cloud.local.file;

import android.os.Environment;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.LocalStorageCloud;

public class RootLocalFolder extends LocalFolder {

	private final LocalStorageCloud localStorageCloud;

	public RootLocalFolder(LocalStorageCloud localStorageCloud) {
		super(null, "", Environment.getExternalStorageDirectory().getPath());
		this.localStorageCloud = localStorageCloud;
	}

	@Override
	public Cloud getCloud() {
		return localStorageCloud;
	}

	@Override
	public RootLocalFolder withCloud(Cloud cloud) {
		return new RootLocalFolder((LocalStorageCloud) cloud);
	}
}
