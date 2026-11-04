package org.cryptomator.presentation.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import org.cryptomator.presentation.R
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Loads thumbnails off the UI thread and keeps the decoded bitmaps in a bounded in-memory cache, so that the file
 * browser never decodes a JPEG while binding or scrolling. A recycled row is told apart by a tag on its [ImageView]: a
 * finished decode is only shown if the view still shows the same thumbnail. The cache is shared by all browse screens.
 */
class ThumbnailLoader @Inject constructor() {

	fun load(thumbnail: File, into: ImageView, @DrawableRes placeholder: Int) {
		val key = thumbnail.absolutePath
		into.setTag(R.id.thumbnail_key, key)
		memoryCache.get(key)?.let {
			into.setImageBitmap(it)
			return
		}
		into.setImageResource(placeholder)
		executor.execute {
			val bitmap = memoryCache.get(key) ?: decode(thumbnail)?.also { memoryCache.put(key, it) }
			if (bitmap != null) {
				mainHandler.post {
					if (into.getTag(R.id.thumbnail_key) == key) {
						into.setImageBitmap(bitmap)
					}
				}
			}
		}
	}

	private fun decode(thumbnail: File): Bitmap? {
		return try {
			BitmapFactory.decodeFile(thumbnail.absolutePath)
		} catch (e: OutOfMemoryError) {
			null
		}
	}

	companion object {

		private const val DECODE_THREADS = 2

		private val mainHandler = Handler(Looper.getMainLooper())
		private val executor = Executors.newFixedThreadPool(DECODE_THREADS)

		private val memoryCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
			override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
		}
	}
}
