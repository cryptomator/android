package org.cryptomator.presentation

import org.cryptomator.domain.executor.PostExecutionThread
import javax.inject.Inject
import javax.inject.Singleton
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

/**
 * MainThread (UI Thread) implementation based on a [io.reactivex.Scheduler]
 * which will execute actions on the Android UI thread
 */
@Singleton
class UIThread @Inject constructor() : PostExecutionThread {

	override fun getScheduler(): Scheduler {
		return AndroidSchedulers.mainThread()
	}
}
