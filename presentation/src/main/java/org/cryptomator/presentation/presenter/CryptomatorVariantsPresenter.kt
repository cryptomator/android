package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.google.common.base.Optional
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateUseCase
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.domain.usecases.UpdateCheck
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.ui.activity.view.CryptomatorVariantsView
import org.cryptomator.presentation.util.FileUtil
import javax.inject.Inject

@PerView
class CryptomatorVariantsPresenter @Inject constructor(
	//
	exceptionMappings: ExceptionHandlers,  //
	private val updateCheckUseCase: DoUpdateCheckUseCase,  //
	private val updateUseCase: DoUpdateUseCase,  //
	private val networkConnectionCheck: NetworkConnectionCheck,  //
	private val fileUtil: FileUtil,  //
) : Presenter<CryptomatorVariantsView>(exceptionMappings) {

	private val fDroidPackageName = "org.fdroid.fdroid"

	fun onInstallMainFDroidVariantClicked() {
		context().packageManager.getLaunchIntentForPackage(fDroidPackageName)?.let {
			it.data = Uri.parse("https://f-droid.org/packages/org.cryptomator.light")
			context().startActivity(it)
		} ?: Toast.makeText(context(), R.string.error_interact_with_fdroid_but_fdroid_missing, Toast.LENGTH_SHORT).show()
	}

	fun onAddRepoClicked() {
		context().packageManager.getLaunchIntentForPackage(fDroidPackageName)?.let {
			it.data = Uri.parse("https://static.cryptomator.org/android/fdroid/repo?fingerprint=F7C3EC3B0D588D3CB52983E9EB1A7421C93D4339A286398E71D7B651E8D8ECDD")
			context().startActivity(it)
		} ?: Toast.makeText(context(), R.string.error_interact_with_fdroid_but_fdroid_missing, Toast.LENGTH_SHORT).show()
	}

	fun onInstallFDroidVariantClicked() {
		context().packageManager.getLaunchIntentForPackage(fDroidPackageName)?.let {
			it.data = Uri.parse("https://f-droid.org/packages/org.cryptomator")
			context().startActivity(it)
		} ?: Toast.makeText(context(), R.string.error_interact_with_fdroid_but_fdroid_missing, Toast.LENGTH_SHORT).show()
	}

	fun onInstallWebsiteVariantClicked() {
		if (networkConnectionCheck.isPresent) {
			view?.showProgress(ProgressModel.GENERIC)

			updateCheckUseCase //
				.withVersion("0.0.0")
				.run(object : NoOpResultHandler<Optional<UpdateCheck>>() {
					override fun onSuccess(result: Optional<UpdateCheck>) {
						installUpdate()
					}

					override fun onError(e: Throwable) {
						view?.showProgress(ProgressModel.COMPLETED)
						showError(e)
					}
				})
		} else {
			Toast.makeText(context(), R.string.error_update_no_internet, Toast.LENGTH_SHORT).show()
		}
	}

	private fun installUpdate() {
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

				override fun onFinished() {
					view?.showProgress(ProgressModel.COMPLETED)
				}
			})
	}

	init {
		unsubscribeOnDestroy(updateCheckUseCase, updateUseCase)
	}
}
