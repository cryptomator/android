package org.cryptomator.data.cloud.local

import android.net.Uri
import android.provider.DocumentsContract
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud

class RootLocalStorageAccessFolder(private val localStorageCloud: LocalStorageCloud) : LocalStorageAccessFolder(
	null,  //
	"",  //
	"",  //
	DocumentsContract.getTreeDocumentId(Uri.parse(localStorageCloud.rootUri())),  //
	DocumentsContract.buildChildDocumentsUriUsingTree( //
		Uri.parse(localStorageCloud.rootUri()),  //
		DocumentsContract.getTreeDocumentId(Uri.parse(localStorageCloud.rootUri()))
	).toString()
) {

	override val cloud: Cloud
		get() = localStorageCloud

	override fun withCloud(cloud: Cloud?): RootLocalStorageAccessFolder {
		return RootLocalStorageAccessFolder(cloud as LocalStorageCloud)
	}
}
