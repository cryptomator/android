package org.cryptomator.presentation.util

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.cryptomator.presentation.CryptomatorApp

class ResourceHelper {

	companion object {

		fun getString(resId: Int): String {
			return CryptomatorApp.applicationContext().getString(resId)
		}

		fun getColor(colorId: Int): Int {
			return ContextCompat.getColor(CryptomatorApp.applicationContext(), colorId)
		}

		fun getDrawable(drawId: Int): Drawable? {
			return ContextCompat.getDrawable(CryptomatorApp.applicationContext(), drawId)
		}

		fun getPixelOffset(dimenId: Int): Int {
			return CryptomatorApp.applicationContext().resources.getDimensionPixelOffset(dimenId)
		}
	}
}
