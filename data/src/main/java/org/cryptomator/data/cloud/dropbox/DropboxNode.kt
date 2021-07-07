package org.cryptomator.data.cloud.dropbox

import org.cryptomator.domain.CloudNode

interface DropboxNode : CloudNode {

	override val parent: DropboxFolder?

}
