package org.cryptomator.presentation.model.comparator

import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel

class CloudNodeModelNameAZComparator : Comparator<CloudNodeModel<*>> {

	override fun compare(o1: CloudNodeModel<*>, o2: CloudNodeModel<*>): Int {
		return if (o1 is CloudFolderModel && o2 is CloudFileModel) {
			-1
		} else if (o1 is CloudFileModel && o2 is CloudFolderModel) {
			1
		} else {
			return o1.name.compareTo(o2.name, true)
		}
	}
}
