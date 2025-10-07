package org.cryptomator.presentation.util

import org.cryptomator.presentation.R
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DateHelper @Inject constructor() {

	private val dateFormatter by lazy {
		SimpleDateFormat("yyyy/MM/dd - HH:mm")
	}

	fun getFormattedModifiedDate(modified: Date?): String? {
		return modified?.let {
			val modifiedAgo = currentDate().time - it.time
			return String.format(ResourceHelper.getString(R.string.screen_file_browser_file_info_label_date), convert(modifiedAgo))
		}
	}

	fun getModifiedDate(modified: Date?): String? {
		return modified?.let {
			dateFormatter.format(it)
		}
	}

	private fun convert(time: Long): String {
		return DurationHandler.values()
			.firstOrNull { it.isApplicable(time) }
			?.convert(time)
			?: DurationHandler.SECONDS.convert(time)
	}

	private enum class DurationHandler(private val period: TimePeriod, private val singularName: Int, private val pluralName: Int) {
		YEARS(periodOfMultipleDays(365), R.string.time_unit_years_sg, R.string.time_unit_years_pl),  //
		MONTHS(periodOfMultipleDays(30), R.string.time_unit_months_sg, R.string.time_unit_months_pl),  //
		WEEKS(periodOfMultipleDays(7), R.string.time_unit_weeks_sg, R.string.time_unit_weeks_pl),  //
		DAYS(period(TimeUnit.DAYS), R.string.time_unit_days_sg, R.string.time_unit_days_pl),  //
		HOURS(period(TimeUnit.HOURS), R.string.time_unit_hours_sg, R.string.time_unit_hours_pl),  //
		MINUTES(period(TimeUnit.MINUTES), R.string.time_unit_minutes_sg, R.string.time_unit_minutes_pl),  //
		SECONDS(period(TimeUnit.SECONDS), R.string.time_unit_seconds_sg, R.string.time_unit_seconds_pl);

		fun convert(timeInMilliseconds: Long): String {
			val value = period.convert(timeInMilliseconds)
			return if (value == 1L) {
				value.toString() + " " + ResourceHelper.getString(singularName)
			} else {
				value.toString() + " " + ResourceHelper.getString(pluralName)
			}
		}

		fun isApplicable(timeInMilliseconds: Long): Boolean {
			return period.convert(timeInMilliseconds) >= 1
		}
	}

	private interface TimePeriod {

		fun convert(timeInMilliseconds: Long): Long
	}

	companion object {

		private fun currentDate(): Date {
			return Date()
		}

		private fun periodOfMultipleDays(numberOfDays: Int): TimePeriod {
			return object : TimePeriod {
				override fun convert(timeInMilliseconds: Long): Long {
					return TimeUnit.DAYS.convert(timeInMilliseconds, TimeUnit.MILLISECONDS) / numberOfDays
				}
			}
		}

		private fun period(unit: TimeUnit): TimePeriod {
			return object : TimePeriod {
				override fun convert(timeInMilliseconds: Long): Long {
					return unit.convert(timeInMilliseconds, TimeUnit.MILLISECONDS)
				}
			}
		}
	}
}
