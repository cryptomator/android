package org.cryptomator.presentation.model.comparator

import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel

class CloudNodeModelSizeBiggestFirstComparator : Comparator<CloudNodeModel<*>> {

	override fun compare(o1: CloudNodeModel<*>?, o2: CloudNodeModel<*>?): Int {
		return if (o1 is CloudFolderModel && o2 is CloudFileModel) {
			-1
		} else if (o1 is CloudFileModel && o2 is CloudFolderModel) {
			1
		} else if (o1 is CloudFolderModel && o2 is CloudFolderModel) {
			return o1.name.compareTo(o2.name, true)
		} else {
			val o1Size = (o1 as CloudFileModel).size
			val o2Size = (o2 as CloudFileModel).size

			return if (o2Size != null && o1Size != null) {
				o2Size.compareTo(o1Size)
			} else if (o2Size != null) {
				-1
			} else if (o1Size != null) {
				1
			} else {
				0
			}
		}
	}
}
