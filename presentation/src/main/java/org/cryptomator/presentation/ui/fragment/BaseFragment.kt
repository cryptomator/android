package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.cryptomator.presentation.di.HasComponent
import org.cryptomator.presentation.di.component.ActivityComponent
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.presenter.Presenter
import org.cryptomator.presentation.util.KeyboardHelper
import timber.log.Timber
import javax.inject.Inject

abstract class BaseFragment : Fragment() {

	@Inject
	lateinit var exceptionMappings: ExceptionHandlers

	private var created: Boolean = false
	private var onViewCreatedCalled: Boolean = false

	private var presenter: Presenter<*>? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		logLifecycle("onCreate")
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		logLifecycle("onCreateView")
		return inflater.inflate(fragmentLayout(), container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		logLifecycle("onViewCreated")
		onViewCreatedCalled = true
	}

	override fun onActivityCreated(savedInstanceState: Bundle?) {
		super.onActivityCreated(savedInstanceState)
		logLifecycle("onActivityCreated")
	}

	override fun onStart() {
		super.onStart()
		logLifecycle("onStart")

		if (!created) {
			Fragments.inject((activity as HasComponent<*>).component as ActivityComponent, this)
			this.presenter = Fragments.initializePresenter(this)
		}

		if (onViewCreatedCalled) {
			setupView()
		}

		if (!created) {
			loadContent()
		} else {
			if (presenter?.isRefreshOnBackpressEnabled() == true) {
				loadContentSilent()
			}
		}

		created = true
		onViewCreatedCalled = false
	}

	override fun onStop() {
		super.onStop()
		logLifecycle("onStop")
	}

	override fun onDestroy() {
		super.onDestroy()
		logLifecycle("onDestroy")
	}

	override fun onResume() {
		super.onResume()
		logLifecycle("onResume")
	}

	override fun onPause() {
		super.onPause()
		logLifecycle("onPause")
	}

	private fun fragmentLayout(): Int =
			javaClass.getAnnotation(org.cryptomator.generator.Fragment::class.java)!!.value

	override fun onDestroyView() {
		super.onDestroyView()
		logLifecycle("onDestroyView")
	}

	internal fun context(): Context = this.requireActivity().applicationContext

	/**
	 * Setup view such as recycler view.
	 */
	protected abstract fun setupView()

	/**
	 * Override if content must be loaded and presented before interaction with the user
	 */
	open fun loadContent() {
		// default empty
	}

	/**
	 * Override if content must be reloaded without displaying progress in the ui
	 */
	open fun loadContentSilent() {
		// default empty
	}

	internal fun hideKeyboard(view: View) {
		KeyboardHelper.hideKeyboard(requireActivity(), view)
	}

	private fun logLifecycle(method: String) {
		Timber.tag("FragmentLifecycle").v("%s %s", method, this)
	}
}
