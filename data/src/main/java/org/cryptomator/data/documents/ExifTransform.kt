package org.cryptomator.data.documents

import android.media.ExifInterface

/**
 * The rotation and mirroring needed to show an image upright, derived from its EXIF orientation tag. The rotation is
 * applied first, then the mirroring.
 */
internal data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean, val flipVertical: Boolean) {

	val isIdentity: Boolean
		get() = rotationDegrees == 0 && !flipHorizontal && !flipVertical

	companion object {

		val NONE = ExifTransform(0, false, false)

		fun forOrientation(orientation: Int): ExifTransform = when (orientation) {
			ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, false, false)
			ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, false, false)
			ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, false, false)
			ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, true, false)
			ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(0, false, true)
			ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90, true, false)
			ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270, true, false)
			else -> NONE
		}
	}
}
