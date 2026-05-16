package org.cryptomator.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.util.file.LruFileCacheUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ThumbnailUtil @Inject constructor(
	context: Context,
	private val cloudContentRepository: DispatchingCloudContentRepository,
) {

	private val executor = Executors.newFixedThreadPool(3)
	private val mainHandler = Handler(Looper.getMainLooper())
	private val thumbnailDir: File = LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.THUMBNAILS)

	init {
		thumbnailDir.mkdirs()
	}

	fun loadThumbnail(file: CloudFileModel, target: ImageView) {
		val cacheKey = cacheKey(file)
		target.tag = cacheKey

		val cachedFile = File(thumbnailDir, "$cacheKey.jpg")
		if (cachedFile.exists()) {
			BitmapFactory.decodeFile(cachedFile.absolutePath)?.let {
				target.setImageBitmap(it)
				return
			}
		}

		executor.execute {
			runCatching {
				val bitmap = when (file.icon) {
					FileIcon.MOVIE -> generateVideoThumbnail(file, cachedFile)
					else -> generateImageThumbnail(file, cachedFile)
				} ?: return@execute

				mainHandler.post {
					if (target.tag == cacheKey) {
						target.setImageBitmap(bitmap)
					}
				}
			}.onFailure { e ->
				Timber.tag("ThumbnailUtil").w(e, "Failed to generate thumbnail for ${file.name}")
			}
		}
	}

	fun cancelLoad(target: ImageView) {
		target.tag = null
	}

	private fun generateImageThumbnail(file: CloudFileModel, cachedFile: File): Bitmap? {
		val bytes = downloadToByteArray(file) ?: return null
		val bitmap = decodeScaledBitmap(bytes) ?: return null
		saveThumbnail(bitmap, cachedFile)
		return bitmap
	}

	private fun generateVideoThumbnail(file: CloudFileModel, cachedFile: File): Bitmap? {
		val tmpFile = File(thumbnailDir, "${cacheKey(file)}.tmp")
		return try {
			downloadToFile(file, tmpFile) ?: return null
			val retriever = MediaMetadataRetriever()
			retriever.use {
				it.setDataSource(tmpFile.absolutePath)
				val frame = it.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
				val bitmap = scaleBitmap(frame) ?: frame
				saveThumbnail(bitmap, cachedFile)
				bitmap
			}
		} finally {
			tmpFile.delete()
		}
	}

	private fun downloadToByteArray(file: CloudFileModel): ByteArray? {
		return try {
			ByteArrayOutputStream().also { out ->
				cloudContentRepository.read(file.toCloudNode(), null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
			}.toByteArray()
		} catch (e: Exception) {
			Timber.tag("ThumbnailUtil").w(e, "Download failed for ${file.name}")
			null
		}
	}

	private fun downloadToFile(file: CloudFileModel, dest: File): File? {
		return try {
			FileOutputStream(dest).use { out ->
				cloudContentRepository.read(file.toCloudNode(), null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
			}
			dest
		} catch (e: Exception) {
			Timber.tag("ThumbnailUtil").w(e, "Download failed for ${file.name}")
			null
		}
	}

	private fun decodeScaledBitmap(bytes: ByteArray): Bitmap? {
		val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
		opts.inSampleSize = computeSampleSize(opts.outWidth, opts.outHeight)
		opts.inJustDecodeBounds = false
		return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
	}

	private fun scaleBitmap(src: Bitmap): Bitmap? {
		val sampleSize = computeSampleSize(src.width, src.height)
		if (sampleSize == 1) return src
		val w = src.width / sampleSize
		val h = src.height / sampleSize
		return Bitmap.createScaledBitmap(src, w, h, true)
	}

	private fun computeSampleSize(width: Int, height: Int): Int {
		var n = 1
		while (minOf(width, height) / (n * 2) >= THUMBNAIL_PX) n *= 2
		return n
	}

	private fun saveThumbnail(bitmap: Bitmap, dest: File) {
		try {
			FileOutputStream(dest).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
		} catch (e: IOException) {
			Timber.tag("ThumbnailUtil").w(e, "Could not save thumbnail to disk")
		}
	}

	private fun cacheKey(file: CloudFileModel): String =
		"${file.path}_${file.size}".hashCode().toString().replace('-', 'n')

	companion object {
		private const val THUMBNAIL_PX = 144
	}
}
