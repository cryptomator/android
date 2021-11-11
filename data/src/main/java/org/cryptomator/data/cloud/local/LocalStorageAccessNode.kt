package org.cryptomator.data.cloud.local

import android.net.Uri
import org.cryptomator.domain.CloudNode

interface LocalStorageAccessNode : CloudNode {

	val uri: Uri?
	override val parent: LocalStorageAccessFolder?
	val documentId: String?
}
