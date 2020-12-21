package org.cryptomator.presentation.model.comparator

import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import kotlin.Comparator

class CloudNodeModelDateOldestFirstComparator : Comparator<CloudNodeModel<*>> {
	override fun compare(o1: CloudNodeModel<*>?, o2: CloudNodeModel<*>?): Int {
		return if (o1 is CloudFolderModel && o2 is CloudFileModel) {
			-1
		} else if (o1 is CloudFileModel && o2 is CloudFolderModel) {
			1
		} else if (o1 is CloudFolderModel && o2 is CloudFolderModel) {
			return o1.name.compareTo(o2.name, true)
		} else {
			val o1ModifyDate = (o1 as CloudFileModel).modified
			val o2ModifyDate = (o2 as CloudFileModel).modified

			return if (o1ModifyDate.isPresent && o2ModifyDate.isPresent) {
				o1ModifyDate.get().compareTo(o2ModifyDate.get())
			} else if (o1ModifyDate.isPresent) {
				-1
			} else if (o2ModifyDate.isPresent) {
				1
			} else {
				0
			}
		}
	}
}
