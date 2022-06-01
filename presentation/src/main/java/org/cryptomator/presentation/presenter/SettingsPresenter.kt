package org.cryptomator.presentation.presenter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.google.common.base.Optional
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateUseCase
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.domain.usecases.UpdateCheck
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.logging.Logfiles
import org.cryptomator.presentation.logging.ReleaseLogger
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.service.PhotoContentJob
import org.cryptomator.presentation.ui.activity.view.SettingsView
import org.cryptomator.presentation.ui.dialog.AskIgnoreBatteryOptimizationsDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppAvailableDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppDialog
import org.cryptomator.presentation.util.EmailBuilder
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.SharedPreferencesHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import timber.log.Timber

@PerView
class SettingsPresenter @Inject internal constructor(
	private val updateCheckUseCase: DoUpdateCheckUseCase,  //
	private val updateUseCase: DoUpdateUseCase,  //
	private val networkConnectionCheck: NetworkConnectionCheck,  //
	exceptionMappings: ExceptionHandlers,  //
	private val fileUtil: FileUtil,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler
) : Presenter<SettingsView>(exceptionMappings) {

	fun checkAutoUploadEnabledAndBatteryOptimizationDisabled() {
		if (sharedPreferencesHandler.usePhotoUpload()) {
			showAskIgnoreBatteryOptimizationsDialogWhenDisabled()
		}
	}

	fun onSendErrorReportClicked() {
		view?.showProgress(ProgressModel.GENERIC)
		// no usecase here because the backend is not involved
		CreateErrorReportArchiveTask().execute()
	}

	fun onDebugModeChanged(enabled: Boolean) {
		ReleaseLogger.updateDebugMode(enabled)
	}

	private fun sendErrorReport(attachment: File) {
		EmailBuilder.anEmail() //
			.to("support@cryptomator.org") //
			.withSubject(context().getString(R.string.error_report_subject)) //
			.withBody(errorReportEmailBody()) //
			.attach(attachment) //
			.send(activity())
	}

	private fun errorReportEmailBody(): String {
		val variant = when (BuildConfig.FLAVOR) {
			"apkstore" -> {
				"APK Store"
			}
			"fdroid" -> {
				"F-Droid"
			}
			"lite" -> {
				"F-Droid Main Repo Edition"
			}
			else -> "Google Play"
		}
		return StringBuilder().append("## ").append(context().getString(R.string.error_report_subject)).append("\n\n") //
			.append("### ").append(context().getString(R.string.error_report_section_summary)).append('\n') //
			.append(context().getString(R.string.error_report_summary_description)).append("\n\n") //
			.append("### ").append(context().getString(R.string.error_report_section_device)).append("\n") //
			.append("Cryptomator v").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") ").append(variant).append("\n") //
			.append("Android ").append(Build.VERSION.RELEASE).append(" / API").append(Build.VERSION.SDK_INT).append("\n") //
			.append("Device ").append(Build.MODEL) //
			.toString()
	}

	fun grantLocalStoragePermissionForAutoUpload() {
		requestPermissions(
			PermissionsResultCallbacks.onLocalStoragePermissionGranted(),  //
			R.string.permission_snackbar_auth_auto_upload,  //
			Manifest.permission.READ_EXTERNAL_STORAGE
		)
	}

	@Callback
	fun onLocalStoragePermissionGranted(result: PermissionsResult) {
		if (result.granted()) {
			PhotoContentJob.scheduleJob(context())
			showAskIgnoreBatteryOptimizationsDialogWhenDisabled()
		} else {
			view?.disableAutoUpload()
		}
	}

	private fun showAskIgnoreBatteryOptimizationsDialogWhenDisabled() {
		val powerManager = context().getSystemService(Context.POWER_SERVICE) as PowerManager
		if (!powerManager.isIgnoringBatteryOptimizations(context().packageName) && !sharedPreferencesHandler.askBatteryOptimizationsDialogDisabled()) {
			view?.showDialog(AskIgnoreBatteryOptimizationsDialog.newInstance())
		}
	}

	fun askIgnoreBatteryOptimizationsAccepted() {
		val intent = Intent()
		intent.action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
		startIntent(intent)
	}

	fun onAskIgnoreBatteryOptimizationsRejected(askAgain: Boolean) {
		if (!askAgain) {
			sharedPreferencesHandler.setAskBatteryOptimizationsDialogDisabled(true)
		}
	}

	fun onCheckUpdateClicked() {
		if (networkConnectionCheck.isPresent) {
			updateCheckUseCase //
				.withVersion(BuildConfig.VERSION_NAME)
				.run(object : NoOpResultHandler<Optional<UpdateCheck>>() {
					override fun onSuccess(result: Optional<UpdateCheck>) {
						if (result.isPresent) {
							updateStatusRetrieved(result.get(), context())
						} else {
							Timber.tag("SettingsPresenter").i("UpdateCheck finished, latest version")
							Toast.makeText(context(), getString(R.string.notification_update_check_finished_latest), Toast.LENGTH_SHORT).show()
						}
						sharedPreferencesHandler.updateExecuted()
						view?.refreshUpdateTimeView()
					}

					override fun onError(e: Throwable) {
						showError(e)
					}
				})
		} else {
			Toast.makeText(context(), R.string.error_update_no_internet, Toast.LENGTH_SHORT).show()
		}
	}

	private fun updateStatusRetrieved(updateCheck: UpdateCheck, context: Context) {
		showNextMessage(updateCheck.releaseNote(), context)
	}

	private fun showNextMessage(message: String, context: Context) {
		if (message.isNotEmpty()) {
			view?.showDialog(UpdateAppAvailableDialog.newInstance(message))
		} else {
			view?.showDialog(UpdateAppAvailableDialog.newInstance(context.getText(R.string.dialog_update_available_message).toString()))
		}
	}

	fun installUpdate() {
		view?.showDialog(UpdateAppDialog.newInstance())
		val uri = fileUtil.contentUriForNewTempFile("cryptomator.apk")
		val file = fileUtil.tempFile("cryptomator.apk")
		updateUseCase //
			.withFile(file) //
			.run(object : NoOpResultHandler<Void?>() {
				override fun onError(e: Throwable) {
					showError(e)
				}

				override fun onSuccess(result: Void?) {
					super.onSuccess(result)
					val intent = Intent(Intent.ACTION_VIEW)
					intent.setDataAndType(uri, "application/vnd.android.package-archive")
					intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
					intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
					context().startActivity(intent)
				}
			})
	}

	private inner class CreateErrorReportArchiveTask : AsyncTask<Void?, IOException?, File?>() {

		override fun doInBackground(vararg params: Void?): File? {
			return try {
				createErrorReportArchive()
			} catch (e: IOException) {
				publishProgress(e)
				null
			}
		}

		override fun onProgressUpdate(vararg values: IOException?) {
			Timber.e(values[0], "Sending error report failed")
			view?.showError(R.string.screen_settings_error_report_failed)
		}

		override fun onPostExecute(attachment: File?) {
			attachment?.let { sendErrorReport(it) }
			view?.showProgress(ProgressModel.COMPLETED)
		}
	}

	@Throws(IOException::class)
	private fun createErrorReportArchive(): File {
		val logfileArchive = prepareLogfileArchive()
		createZipArchive(logfileArchive, Logfiles.logfiles(context()))
		return logfileArchive
	}

	@Throws(IOException::class)
	private fun prepareLogfileArchive(): File {
		val logsDir = File(activity().cacheDir, "logs")
		if (!logsDir.exists() && !logsDir.mkdirs()) {
			throw IOException("Failed to create logs directory")
		}
		val logfileArchive = File(logsDir, "logs.zip")
		deleteIfExists(logfileArchive)
		return logfileArchive
	}

	@Throws(IOException::class)
	private fun createZipArchive(target: File, entries: Iterable<File>) {
		ZipOutputStream(FileOutputStream(target)).use { logs ->
			Logfiles.existingLogfiles(activity()).forEach { logfile ->
				addLogfile(logs, logfile)
			}
		}
	}

	@Throws(IOException::class)
	private fun addLogfile(logs: ZipOutputStream, logfile: File) {
		val entry = ZipEntry(logfile.name)
		entry.time = logfile.lastModified()
		logs.putNextEntry(entry)
		FileInputStream(logfile).use { inputStream ->
			val buffer = ByteArray(4096)
			var count = 0
			while (count != EOF) {
				logs.write(buffer, 0, count)
				count = inputStream.read(buffer)
			}
		}
	}

	private fun deleteIfExists(file: File) {
		if (file.exists()) {
			// noinspection ResultOfMethodCallIgnored
			file.delete()
		}
	}

	companion object {

		private const val EOF = -1
	}

	init {
		unsubscribeOnDestroy(updateCheckUseCase, updateUseCase)
	}
}
