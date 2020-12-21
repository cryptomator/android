package org.cryptomator.presentation.util

import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.util.Blacklist.Entry
import javax.inject.Inject

class FileNameBlacklist @Inject constructor() : Blacklist<CloudFileModel> {

	private val dotUnderscorePrefix = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith("._")
		}
	}

	private val dsStoreFile = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith(".DS_Store")
		}
	}

	private val thumbsFile = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith("Thumbs.db")
		}
	}

	private val entries = listOf(dotUnderscorePrefix, dsStoreFile, thumbsFile)

	override fun isBlacklisted(cloudNodeModel: CloudFileModel): Boolean {
		entries.forEach { entry ->
			if (entry.isBlacklisted(cloudNodeModel.name)) {
				return true
			}
		}
		return false
	}
}
