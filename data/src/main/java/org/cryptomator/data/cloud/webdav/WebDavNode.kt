package org.cryptomator.data.cloud.webdav

import org.cryptomator.domain.CloudNode

interface WebDavNode : CloudNode {

	override val parent: WebDavFolder?

}
