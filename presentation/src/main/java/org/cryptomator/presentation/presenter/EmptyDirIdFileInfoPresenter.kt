package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.EmptyDirFileView
import javax.inject.Inject

@PerView
class EmptyDirIdFileInfoPresenter @Inject constructor(exceptionMappings: ExceptionHandlers) : Presenter<EmptyDirFileView>(exceptionMappings) {
	fun onShowMoreInfoButtonPressed() {
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = Uri.parse("https://cryptomator.org/help/articles/sanitizer")
		context().startActivity(intent)
	}
}
