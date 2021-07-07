package org.cryptomator.presentation.ui.activity

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.TextEditorIntent
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.ui.dialog.UnsavedChangesDialog
import org.cryptomator.presentation.ui.fragment.TextEditorFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity
class TextEditorActivity : BaseActivity(),
	TextEditorView,
	UnsavedChangesDialog.Callback,
	SearchView.OnQueryTextListener {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	@InjectIntent
	lateinit var textEditorIntent: TextEditorIntent

	override val textFileContent: String
		get() = textEditorFragment().textFileContent

	override fun setupView() {
		textEditorPresenter.setTextFile(textEditorIntent.textFile())
		setupToolbar()
	}

	override fun createFragment(): Fragment = TextEditorFragment()

	override fun onBackPressed() {
		textEditorPresenter.onBackPressed()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)

		menu.findItem(R.id.action_search)
			.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
				override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
					menu.findItem(R.id.action_search_previous).isVisible = true
					menu.findItem(R.id.action_search_next).isVisible = true
					return true
				}

				override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
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

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		val searchView = menu.findItem(R.id.action_search).actionView as SearchView
		searchView.setOnQueryTextListener(this)

		return super.onPrepareOptionsMenu(menu)
	}

	private fun setupToolbar() {
		toolbar.title = textEditorIntent.textFile().name
		setSupportActionBar(toolbar)
	}

	override fun performBackPressed() {
		super.onBackPressed()
	}

	override fun showUnsavedChangesDialog() {
		UnsavedChangesDialog.withContext(this).show()
	}

	override fun displayTextFileContent(textFileContent: String) {
		textEditorFragment().displayTextFileContent(textFileContent)
	}

	override fun onSaveChangesClicked() {
		textEditorPresenter.saveChanges()
	}

	override fun onDiscardChangesClicked() {
		performBackPressed()
	}

	override fun vaultExpectedToBeUnlocked() {
		finish()
	}

	private fun textEditorFragment(): TextEditorFragment = getCurrentFragment(R.id.fragmentContainer) as TextEditorFragment

}
