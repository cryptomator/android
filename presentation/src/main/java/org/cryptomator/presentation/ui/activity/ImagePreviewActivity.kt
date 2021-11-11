package org.cryptomator.presentation.ui.activity

import android.net.Uri
import android.view.View.GONE
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.ImagePreviewIntent
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ImagePreviewFile
import org.cryptomator.presentation.presenter.ImagePreviewPresenter
import org.cryptomator.presentation.ui.activity.view.ImagePreviewView
import org.cryptomator.presentation.ui.dialog.ConfirmDeleteCloudNodeDialog
import org.cryptomator.presentation.ui.fragment.ImagePreviewFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_image_preview.controlView
import kotlinx.android.synthetic.main.activity_image_preview.deleteImage
import kotlinx.android.synthetic.main.activity_image_preview.exportImage
import kotlinx.android.synthetic.main.activity_image_preview.shareImage
import kotlinx.android.synthetic.main.activity_image_preview.toolbar
import kotlinx.android.synthetic.main.activity_image_preview.viewPager

@Activity(layout = R.layout.activity_image_preview)
class ImagePreviewActivity : BaseActivity(), ImagePreviewView, ConfirmDeleteCloudNodeDialog.Callback {

	@Inject
	lateinit var presenter: ImagePreviewPresenter

	@InjectIntent
	lateinit var imagePreviewIntent: ImagePreviewIntent

	private lateinit var imagePreviewSliderAdapter: ImagePreviewSliderAdapter

	lateinit var imagePreviewFiles: ArrayList<ImagePreviewFile>

	private val currentImageUri: Uri?
		get() = imagePreviewFiles[imagePreviewSliderAdapter.getIndex(viewPager.currentItem)].uri

	private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {

		override fun onPageSelected(position: Int) {
			onImageChanged(position)
		}
	}

	override fun setupView() {
		try {
			val imagePreviewFileStore = presenter.getImagePreviewFileStore(imagePreviewIntent.withImagePreviewFiles())

			val index = imagePreviewFileStore.index
			imagePreviewFiles = presenter.getImagePreviewFiles(imagePreviewFileStore, index)

			deleteImage.setOnClickListener {
				presenter.onDeleteImageClicked(imagePreviewFiles[imagePreviewSliderAdapter.getIndex(viewPager.currentItem)])
			}
			exportImage.setOnClickListener {
				currentImageUri?.let { presenter.exportImageToUserSelectedLocation(it) }
			}
			shareImage.setOnClickListener {
				currentImageUri?.let { presenter.onShareImageClicked(it) }
			}

			setupViewPager(index)
			setupToolbar(index)
			setupStatusBar()
			toggleFullScreen()
			attachSystemUiVisibilityChangeListener()
		} catch (e: FatalBackendException) {
			showError(getString(R.string.error_generic))
			finish()
		}
	}

	private fun setupViewPager(index: Int) {
		imagePreviewSliderAdapter = ImagePreviewSliderAdapter(supportFragmentManager)
		viewPager.adapter = imagePreviewSliderAdapter
		viewPager.currentItem = index
		viewPager.addOnPageChangeListener(pageChangeListener)
		viewPager.pageMargin = 50
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) {
			hideStatusBar()
		}
	}

	private fun setupStatusBar() {
		window.statusBarColor = ContextCompat.getColor(this, R.color.colorBlack)
	}

	private fun setupToolbar(index: Int) {
		updateTitle(index)
		setSupportActionBar(toolbar)

		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_clear)
	}

	private fun updateTitle(position: Int) {
		toolbar.title = imagePreviewFiles[imagePreviewSliderAdapter.getIndex(position)].cloudFileModel.name
	}

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		android.R.id.home -> {
			// finish this activity and does not call the onCreate method of the parent activity
			finish()
			true
		}
		else -> super.onMenuItemSelected(itemId)
	}

	private fun attachSystemUiVisibilityChangeListener() {
		window.decorView.setOnSystemUiVisibilityChangeListener { flags ->
			val visible = flags and SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
			setControlViewVisibility(if (visible) VISIBLE else GONE)
		}
	}

	/**
	 * Show or hide full screen.
	 */
	private fun toggleFullScreen() {
		var newUiOptions = window.decorView.systemUiVisibility
		newUiOptions = newUiOptions or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		newUiOptions = newUiOptions xor SYSTEM_UI_FLAG_FULLSCREEN
		newUiOptions = newUiOptions xor SYSTEM_UI_FLAG_IMMERSIVE_STICKY

		window.decorView.systemUiVisibility = newUiOptions
	}

	private fun hideStatusBar() {
		var newUiOptions = window.decorView.systemUiVisibility
		newUiOptions = newUiOptions or SYSTEM_UI_FLAG_FULLSCREEN

		window.decorView.systemUiVisibility = newUiOptions
	}

	override fun hideSystemUi() {
		toggleNavigationBar()
		hideToolbar()
	}

	/**
	 * Show or hide navigation bar.
	 */
	private fun toggleNavigationBar() {
		var newUiOptions = window.decorView.systemUiVisibility

		newUiOptions = newUiOptions xor SYSTEM_UI_FLAG_HIDE_NAVIGATION

		window.decorView.systemUiVisibility = newUiOptions
	}

	override fun showSystemUi() {
		toggleNavigationBar()
		showToolbar()
	}

	override fun showImagePreview(imagePreviewFile: ImagePreviewFile) {
		fragmentFor(imagePreviewFile)?.showAndUpdateImage(imagePreviewFile)
	}

	private fun fragmentFor(imagePreviewFile: ImagePreviewFile): ImagePreviewFragment? {
		return supportFragmentManager.fragments
			.map { it as ImagePreviewFragment }
			.firstOrNull { it.imagePreviewFile() == imagePreviewFile }
	}

	override fun hideProgressBar(imagePreviewFile: ImagePreviewFile) {
		fragmentFor(imagePreviewFile)?.hideProgressBar()
	}

	override fun vaultExpectedToBeUnlocked() {
		finish()
	}

	override fun onDeleteCloudNodeConfirmed(nodes: List<CloudNodeModel<*>>) {
		presenter.onDeleteImageConfirmed(imagePreviewFiles[imagePreviewSliderAdapter.getIndex(viewPager.currentItem)], viewPager.currentItem)
	}

	override fun onImageDeleted(index: Int) {
		imagePreviewSliderAdapter.deletePage(index)

		presenter.pageIndexes.size.let {
			when {
				it == 0 -> {
					showMessage(getString(R.string.dialog_no_more_images_to_display))
					finish()
				}
				it > index -> updateTitle(index)
				it <= index -> updateTitle(index - 1)
			}
		}
	}

	private fun setControlViewVisibility(visibility: Int) {
		controlView.visibility = visibility
	}

	private fun onImageChanged(position: Int) {
		updateTitle(position)
	}

	private fun hideToolbar() {
		supportActionBar?.hide()
	}

	private fun showToolbar() {
		supportActionBar?.show()
	}

	inner class ImagePreviewSliderAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

		init {
			initPageIndexes()
		}

		override fun getItem(position: Int): Fragment {
			return ImagePreviewFragment.newInstance(imagePreviewFiles[presenter.pageIndexes[position]])
		}

		override fun getCount(): Int = presenter.pageIndexes.size

		// This is called when notifyDataSetChanged() is called
		override fun getItemPosition(`object`: Any): Int {
			// refresh all fragments when data set changed
			return PagerAdapter.POSITION_NONE
		}

		// Delete a page at a `position`
		fun deletePage(position: Int) {
			// Remove the corresponding item in the data set
			presenter.pageIndexes.removeAt(position)
			// Notify the adapter that the data set is changed
			notifyDataSetChanged()
		}

		fun getIndex(position: Int): Int {
			return presenter.pageIndexes[position]
		}

		private fun initPageIndexes() {
			presenter.pageIndexes = ArrayList()

			(0 until imagePreviewFiles.size).forEach { i ->
				presenter.pageIndexes.add(i)
			}
		}
	}

}
