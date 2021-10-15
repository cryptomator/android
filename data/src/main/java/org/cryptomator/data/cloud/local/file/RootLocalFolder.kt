package org.cryptomator.data.cloud.local.file

import android.os.Environment
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud

class RootLocalFolder(private val localStorageCloud: LocalStorageCloud) : LocalFolder(null, "", Environment.getExternalStorageDirectory().path) {

	override val cloud: Cloud
		get() = localStorageCloud

	override fun withCloud(cloud: Cloud?): RootLocalFolder {
		return RootLocalFolder(cloud as LocalStorageCloud)
	}
}
