package org.cryptomator.data.cloud.local.storageaccessframework;

import android.os.Build;
import android.provider.DocumentsContract;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.LocalStorageCloud;

import androidx.annotation.RequiresApi;

import static android.net.Uri.parse;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class RootLocalStorageAccessFolder extends LocalStorageAccessFolder {

	private final LocalStorageCloud localStorageCloud;

	public RootLocalStorageAccessFolder(LocalStorageCloud localStorageCloud) {
		super(null, //
				"", //
				"", //
				DocumentsContract.getTreeDocumentId( //
						parse(localStorageCloud.rootUri())), //
				DocumentsContract.buildChildDocumentsUriUsingTree( //
						parse(localStorageCloud.rootUri()), //
						DocumentsContract.getTreeDocumentId( //
								parse(localStorageCloud.rootUri())))
						.toString());
		this.localStorageCloud = localStorageCloud;
	}

	@Override
	public Cloud getCloud() {
		return localStorageCloud;
	}

	@Override
	public LocalStorageAccessFolder withCloud(Cloud cloud) {
		return new RootLocalStorageAccessFolder((LocalStorageCloud) cloud);
	}
}
