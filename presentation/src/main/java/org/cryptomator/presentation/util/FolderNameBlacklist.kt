package org.cryptomator.presentation.util

import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.util.Blacklist.Entry
import javax.inject.Inject

class FolderNameBlacklist @Inject constructor() : Blacklist<CloudFolderModel> {

	private val dotUnderscorePrefix = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith("._")
		}
	}

	private val temporaryItemsFolder = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith(".TemporaryItems")
		}
	}

	private val syncFolder = object : Entry {
		override fun isBlacklisted(name: String): Boolean {
			return name.startsWith(".sync")
		}
	}

	private val entries = listOf(dotUnderscorePrefix, temporaryItemsFolder, syncFolder)

	override fun isBlacklisted(cloudNodeModel: CloudFolderModel): Boolean {
		return entries.any { it.isBlacklisted(cloudNodeModel.name) }
	}
}
