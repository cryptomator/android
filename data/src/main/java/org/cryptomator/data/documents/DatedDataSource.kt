package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.util.Optional
import java.util.Date

/**
 * Hands [delegate] to the cloud with [modifiedDate] instead of its own modification date, e.g. a local copy with that of
 * the file it was copied from.
 */
internal class DatedDataSource(private val delegate: DataSource, private val modifiedDate: Date) : DataSource by delegate {

	override fun modifiedDate(context: Context): Optional<Date> {
		return Optional.of(modifiedDate)
	}
}
