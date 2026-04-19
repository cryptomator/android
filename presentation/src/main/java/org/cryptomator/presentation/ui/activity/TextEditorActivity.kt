package org.cryptomator.presentation.ui.activity

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.TextEditorIntent
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.ui.dialog.UnsavedChangesDialog
import org.cryptomator.presentation.ui.fragment.TextEditorFragment
import javax.inject.Inject

@Activity
class TextEditorActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate),
	TextEditorView,
	UnsavedChangesDialog.Callback,
	SearchView.OnQueryTextListener {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	@Inject
	lateinit var licenseEnforcer: LicenseEnforcer

	@InjectIntent
	lateinit var textEditorIntent: TextEditorIntent

	/**
	 * Determine whether the activity currently allows modifying the opened text file.
	 *
	 * Considers both the app's license state and the incoming intent's hub write permission.
	 *
	 * @return `true` if writing is allowed, `false` otherwise.
	 */
	private fun hasWriteAccess(): Boolean {
		return licenseEnforcer.hasWriteAccess() || textEditorIntent.hubWriteAllowed() == true
	}

	override val textFileContent: String
		get() = textEditorFragment().textFileContent

	override fun setupView() {
		textEditorPresenter.setTextFile(textEditorIntent.textFile())
		setupToolbar()
	}

	/**
 * Creates a new TextEditorFragment to host in this activity.
 *
 * @return A new instance of TextEditorFragment.
 */
override fun createFragment(): Fragment = TextEditorFragment()

	/**
	 * Handles the system back navigation: if write access is available, delegates handling to the presenter; otherwise invokes the default back behavior.
	 */
	override fun onBackPressed() {
		if (!hasWriteAccess()) {
			super.onBackPressed()
			return
		}
		textEditorPresenter.onBackPressed()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)

		menu.findItem(R.id.action_search)
			.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
				override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
					menu.findItem(R.id.action_search_previous).isVisible = true
					menu.findItem(R.id.action_search_next).isVisible = true
					return true
				}

				override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
					invalidateOptionsMenu()
					return true
				}
			})
		return true
	}

	override fun getCustomMenuResource(): Int = R.menu.menu_text_editor

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		R.id.action_save_changes -> {
			textEditorPresenter.saveChanges()
			true
		}
		R.id.action_search_previous -> {
			textEditorFragment().onPreviousQuery()
			true
		}
		R.id.action_search_next -> {
			textEditorFragment().onNextQuery()
			true
		}
		else -> {
			super.onMenuItemSelected(itemId)
		}
	}

	override fun onQueryTextSubmit(query: String): Boolean {
		textEditorFragment().onQueryText(query)
		return true
	}

	override fun onQueryTextChange(query: String): Boolean {
		if (sharedPreferencesHandler.useLiveSearch()) {
			textEditorFragment().onQueryText(query)
		}

		return true
	}

	/**
	 * Prepares the options menu by wiring the search view's query listener and showing or hiding the save action based on write access.
	 *
	 * @return The result of calling the superclass implementation of `onPrepareOptionsMenu`.
	 */
	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		val searchView = menu.findItem(R.id.action_search).actionView as SearchView
		searchView.setOnQueryTextListener(this)

		menu.findItem(R.id.action_save_changes).isVisible = hasWriteAccess()

		return super.onPrepareOptionsMenu(menu)
	}

	private fun setupToolbar() {
		binding.mtToolbar.toolbar.title = textEditorIntent.textFile().name
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun performBackPressed() {
		super.onBackPressed()
	}

	override fun showUnsavedChangesDialog() {
		UnsavedChangesDialog.withContext(this).show()
	}

	/**
	 * Displays the provided text file content in the editor UI.
	 *
	 * If the current session does not have write access, the editor is set to read-only after displaying the content.
	 *
	 * @param textFileContent The text content of the file to display in the editor.
	 */
	override fun displayTextFileContent(textFileContent: String) {
		textEditorFragment().displayTextFileContent(textFileContent)
		if (!hasWriteAccess()) {
			textEditorFragment().setReadOnly()
		}
	}

	/**
	 * Requests that any unsaved changes to the current text file be saved.
	 */
	override fun onSaveChangesClicked() {
		textEditorPresenter.saveChanges()
	}

	override fun onDiscardChangesClicked() {
		performBackPressed()
	}

	/**
	 * Closes the activity when the vault is expected to be unlocked.
	 */
	override fun vaultExpectedToBeUnlocked() {
		finish()
	}

	/**
 * Retrieve the currently displayed TextEditorFragment from the fragment container.
 *
 * @return The active TextEditorFragment instance.
 */
private fun textEditorFragment(): TextEditorFragment = getCurrentFragment(R.id.fragment_container) as TextEditorFragment
}
