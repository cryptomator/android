package org.cryptomator.presentation.presenter

import android.content.Context
import android.net.Uri
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.util.Optional
import java.io.IOException
import java.io.InputStream
import java.util.Date

class UriBasedDataSource private constructor(private val uri: Uri) : DataSource {

	override fun modifiedDate(context: Context): Date? {
		return ContentResolverUtil(context).fileModifiedDate(uri)
	}

	override fun size(context: Context): Long? {
		return ContentResolverUtil(context).fileSize(uri)
	}

	@Throws(IOException::class)
	override fun open(context: Context): InputStream? {
		return ContentResolverUtil(context).openInputStream(uri)
	}

	override fun decorate(delegate: DataSource): DataSource {
		return delegate
	}

	@Throws(IOException::class)
	override fun close() {
		// do nothing
	}

	override fun modifiedDate(context: Context): Optional<Date> {
		return Optional.ofNullable(ContentResolverUtil(context).fileModifiedDate(uri))
	}

	companion object {

		@JvmStatic
		fun from(uri: Uri): UriBasedDataSource {
			return UriBasedDataSource(uri)
		}
	}
}
