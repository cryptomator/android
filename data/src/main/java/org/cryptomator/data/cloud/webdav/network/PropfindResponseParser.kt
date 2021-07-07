package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.cloud.webdav.WebDavFolder
import org.cryptomator.domain.exception.FatalBackendException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Date
import timber.log.Timber

internal class PropfindResponseParser(private val requestedFolder: WebDavFolder) {

	private var xmlPullParser: XmlPullParser

	@Throws(XmlPullParserException::class, IOException::class)
	fun parse(responseBody: InputStream?): List<PropfindEntryData> {
		val entryData: MutableList<PropfindEntryData> = ArrayList()
		xmlPullParser.setInput(responseBody, "UTF-8")
		while (skipToStartOf(TAG_RESPONSE)) {
			val entry = parseResponse()
			if (entry != null) {
				entryData.add(entry)
			}
		}
		return entryData
	}

	@Throws(XmlPullParserException::class, IOException::class)
	private fun skipToStartOf(tag: String): Boolean {
		do {
			xmlPullParser.next()
		} while (!endOfDocument() && !startOf(tag))
		return startOf(tag)
	}

	@Throws(XmlPullParserException::class, IOException::class)
	private fun parseResponse(): PropfindEntryData? {
		var entry: PropfindEntryData? = null
		var path: String? = null
		while (nextTagUntilEndOf(TAG_RESPONSE)) {
			if (tagIs(TAG_PROPSTAT)) {
				entry = defaultIfNull(parsePropstatWith200Status(), entry)
			} else if (tagIs(TAG_HREF)) {
				path = textInCurrentTag().trim { it <= ' ' }
			}
		}
		if (entry == null) {
			Timber.tag("WebDAV").w("No propstat element with 200 status in response element. Entry ignored.")
			Timber.tag("WebDAV").v("No propstat element with 200 status in response element. Entry ignored. Dir: %s, Path: %s", requestedFolder.path, path)
			return null
		}
		if (path == null) {
			Timber.tag("WebDAV").w("Missing href in response element. Entry ignored.")
			Timber.tag("WebDAV").v("Missing href in response element. Entry ignored. Dir: %s", requestedFolder.path)
			return null
		}
		entry.setPath(path)
		return entry
	}

	@Throws(IOException::class, XmlPullParserException::class)
	private fun parsePropstatWith200Status(): PropfindEntryData? {
		val result = PropfindEntryData()
		var statusOk = false
		while (nextTagUntilEndOf(TAG_PROPSTAT)) {
			when {
				tagIs(TAG_STATUS) -> {
					val text = textInCurrentTag().trim { it <= ' ' }
					val statusSegments = text.split(" ".toRegex()).toTypedArray()
					val code = if (statusSegments.isNotEmpty()) statusSegments[1] else ""
					statusOk = STATUS_OK == code
				}
				tagIs(TAG_COLLECTION) -> {
					result.setFile(false)
				}
				tagIs(TAG_LAST_MODIFIED) -> {
					result.setLastModified(parseDate(textInCurrentTag()))
				}
				tagIs(TAG_CONTENT_LENGTH) -> {
					result.size = parseLong(textInCurrentTag())
				}
			}
		}
		return if (statusOk) {
			result
		} else {
			null
		}
	}

	@Throws(XmlPullParserException::class, IOException::class)
	private fun nextTagUntilEndOf(tag: String): Boolean {
		do {
			xmlPullParser.next()
		} while (!endOfDocument() && !startOfATag() && !endOf(tag))
		return startOfATag()
	}

	@Throws(XmlPullParserException::class)
	private fun startOf(tag: String): Boolean {
		return startOfATag() && tagIs(tag)
	}

	private fun tagIs(tag: String): Boolean {
		return tag.equals(localName(), ignoreCase = true)
	}

	@Throws(XmlPullParserException::class)
	private fun startOfATag(): Boolean {
		return xmlPullParser.eventType == XmlPullParser.START_TAG
	}

	@Throws(XmlPullParserException::class)
	private fun endOf(tag: String): Boolean {
		return xmlPullParser.eventType == XmlPullParser.END_TAG && tag.equals(localName(), ignoreCase = true)
	}

	private fun localName(): String {
		val rawName = xmlPullParser.name
		val namespaceAndLocalName = rawName.split(":".toRegex(), 2).toTypedArray()
		return namespaceAndLocalName[namespaceAndLocalName.size - 1]
	}

	@Throws(XmlPullParserException::class)
	private fun endOfDocument(): Boolean {
		return xmlPullParser.eventType == XmlPullParser.END_DOCUMENT
	}

	@Throws(IOException::class, XmlPullParserException::class)
	private fun textInCurrentTag(): String {
		check(startOfATag()) { "textInCurrentTag may only be called at start of a tag" }
		val result = StringBuilder()
		var ident = 0
		do {
			when (xmlPullParser.next()) {
				XmlPullParser.TEXT -> result.append(xmlPullParser.text)
				XmlPullParser.START_TAG -> ident++
				XmlPullParser.END_TAG -> ident--
			}
		} while (!endOfDocument() && ident >= 0)
		return result.toString()
	}

	private fun defaultIfNull(value: PropfindEntryData?, defaultValue: PropfindEntryData?): PropfindEntryData? {
		return value ?: defaultValue
	}

	private fun parseDate(text: String): Date? {
		return try {
			Date(text)
		} catch (e: IllegalArgumentException) {
			null
		}
	}

	private fun parseLong(text: String): Long? {
		return try {
			text.toLong()
		} catch (e: NumberFormatException) {
			null
		}
	}

	companion object {

		private const val TAG_RESPONSE = "response"
		private const val TAG_HREF = "href"
		private const val TAG_COLLECTION = "collection"
		private const val TAG_LAST_MODIFIED = "getlastmodified"
		private const val TAG_CONTENT_LENGTH = "getcontentlength"
		private const val TAG_PROPSTAT = "propstat"
		private const val TAG_STATUS = "status"
		private const val STATUS_OK = "200"
	}

	init {
		try {
			xmlPullParser = XmlPullParserFactory.newInstance().newPullParser()
		} catch (e: XmlPullParserException) {
			throw FatalBackendException(e)
		}
	}
}
