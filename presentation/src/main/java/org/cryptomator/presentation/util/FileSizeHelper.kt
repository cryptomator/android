package org.cryptomator.presentation.util

import android.content.Context
import org.cryptomator.presentation.R
import org.cryptomator.util.Optional
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

class FileSizeHelper @Inject constructor(private val context: Context) {

	private val units: Array<String> = arrayOf( //
		context.getString(R.string.file_size_unit_bytes), //
		context.getString(R.string.file_size_unit_kilo_bytes), //
		context.getString(R.string.file_size_unit_mega_bytes), //
		context.getString(R.string.file_size_unit_giga_bytes), //
		context.getString(R.string.file_size_unit_tera_bytes)
	)

	fun getFormattedFileSize(size: Optional<Long>): Optional<String> {
		return when {
			size.isAbsent -> {
				Optional.empty()
			}
			size.get() == 0L -> {
				wrap(context.getString(R.string.file_size_zero))
			}
			else -> {
				val digitGroups = min(log10(size.get().toDouble()) / log10(1000.0), units.size - 1.toDouble()).toInt()
				val formatPattern = if (digitGroups < 2) "##0" else "##0.#"
				wrap(DecimalFormat(formatPattern).format(size.get() / 1000.0.pow(digitGroups.toDouble())) + " " + units[digitGroups])
			}
		}
	}

	private fun wrap(value: String): Optional<String> {
		return Optional.of(String.format(ResourceHelper.getString(R.string.screen_file_browser_file_info_label_size), value))
	}

}
