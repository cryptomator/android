package org.cryptomator.presentation.model.comparator

import android.content.Context
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import java.util.Comparator

class CloudModelComparator(private val context: Context) : Comparator<CloudModel> {

	override fun compare(o1: CloudModel, o2: CloudModel): Int {
		return if (o1 is WebDavCloudModel && o2 is WebDavCloudModel) {
			o1.url().compareTo(o2.url().uppercase(), ignoreCase = true)
		} else {
			context.getString(o1.name()).compareTo(context.getString(o2.name()), ignoreCase = true)
		}
	}
}
