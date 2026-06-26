package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentTextEditorBinding
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.layout.applySystemBarsMargins
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import org.cryptomator.presentation.ui.layout.attachFastScrollThumb
import javax.inject.Inject

@Fragment
class TextEditorFragment : BaseFragment<FragmentTextEditorBinding>(FragmentTextEditorBinding::inflate) {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	private var fastScrollCleanup: (() -> Unit)? = null
	private var caretAutoScrollWatcher: TextWatcher? = null

	val textFileContent: String
		get() = binding.textEditor.text.toString()

	override fun setupView() {
		// no-op
	}

	override fun loadContent() {
		textEditorPresenter.loadFileContent()
	}

	fun displayTextFileContent(textFileContent: String?) {
		binding.textEditor.setText(textFileContent)
	}

	fun setReadOnly() {
		binding.textEditor.isFocusable = false
		binding.textEditor.isFocusableInTouchMode = false
		binding.textEditor.isCursorVisible = false
	}

	fun onQueryText(query: String) {
		textEditorPresenter.query = query

		clearSpans(binding.textEditor)

		if (query.isEmpty()) {
			return
		}

		textEditorPresenter.lastFilterLocation = -1

		onNextQuery()
	}

	fun onPreviousQuery() {
		onQuery(Direction.PREVIOUS)
	}

	fun onNextQuery() {
		onQuery(Direction.NEXT)
	}

	private fun onQuery(direction: Direction) {
		if (textEditorPresenter.query == null) {
			return
		}

		clearSpans(binding.textEditor)

		val fulltext = binding.textEditor.text.toString().lowercase()

		textEditorPresenter.query?.lowercase()?.let {
			val index: Int = when (direction) {
				Direction.PREVIOUS -> {
					textEditorPresenter.lastFilterLocation -= 1

					if (textEditorPresenter.lastFilterLocation < 0) {
						return
					}

					fulltext.lastIndexOf(it, textEditorPresenter.lastFilterLocation)
				}
				Direction.NEXT -> {
					textEditorPresenter.lastFilterLocation += 1
					fulltext.indexOf(it, textEditorPresenter.lastFilterLocation)
				}
			}

			if (index < 0) {
				return
			}

			binding.textEditor.text?.setSpan(
				BackgroundColorSpan(ContextCompat.getColor(context(), R.color.colorPrimaryTransparent)),
				index,
				index + it.length,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)

			textEditorPresenter.lastFilterLocation = index

			binding.textEditor.setSelection(index, index + it.length)
			binding.textEditor.post { scrollCaretIntoView() }
		}
	}

	private fun clearSpans(@NonNull editable: TextInputEditText) {
		editable.text
			?.getSpans(0, editable.length(), BackgroundColorSpan::class.java)
			?.forEach { span ->
				editable.text?.removeSpan(span)
			}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.textViewWrapper.applySystemBarsPadding(left = true, right = true, bottom = true)
		binding.scrollThumb.applySystemBarsMargins(end = true, bottom = true)
		binding.scrollTrack.applySystemBarsMargins(end = true, bottom = true)
		fastScrollCleanup = binding.textViewWrapper.attachFastScrollThumb(binding.scrollThumb, binding.scrollTrack, binding.textEditor)
		setupCaretAutoScroll()
	}

	override fun onDestroyView() {
		fastScrollCleanup?.invoke()
		fastScrollCleanup = null
		caretAutoScrollWatcher?.let { binding.textEditor.removeTextChangedListener(it) }
		caretAutoScrollWatcher = null
		super.onDestroyView()
	}

	private fun setupCaretAutoScroll() {
		caretAutoScrollWatcher = binding.textEditor.doAfterTextChanged {
			binding.textEditor.post { scrollCaretIntoView() }
		}
	}

	private fun scrollCaretIntoView() {
		val editor = binding.textEditor
		val scroll = binding.textViewWrapper
		val layout = editor.layout ?: return
		val line = layout.getLineForOffset(editor.selectionEnd)
		val lineTop = editor.paddingTop + layout.getLineTop(line)
		val lineBottom = editor.paddingTop + layout.getLineBottom(line)
		val visibleHeight = scroll.height - scroll.paddingTop - scroll.paddingBottom
		when {
			lineTop < scroll.scrollY -> scroll.smoothScrollTo(0, lineTop)
			lineBottom > scroll.scrollY + visibleHeight -> scroll.smoothScrollTo(0, lineBottom - visibleHeight)
		}
	}

	enum class Direction { PREVIOUS, NEXT }
}
