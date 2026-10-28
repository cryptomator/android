package org.cryptomator.data.documents

import android.media.ExifInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExifTransformTest {

	@Test
	fun normalIsIdentity() {
		val transform = ExifTransform.forOrientation(ExifInterface.ORIENTATION_NORMAL)
		assertEquals(ExifTransform(0, false, false), transform)
		assertTrue(transform.isIdentity)
	}

	@Test
	fun undefinedOrUnknownOrientationIsIdentity() {
		assertEquals(ExifTransform.NONE, ExifTransform.forOrientation(ExifInterface.ORIENTATION_UNDEFINED))
		assertEquals(ExifTransform.NONE, ExifTransform.forOrientation(0))
	}

	@Test
	fun rotationsMapToDegreesWithoutFlips() {
		assertEquals(ExifTransform(90, false, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_ROTATE_90))
		assertEquals(ExifTransform(180, false, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_ROTATE_180))
		assertEquals(ExifTransform(270, false, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_ROTATE_270))
	}

	@Test
	fun flipsMapToMirroringWithoutRotation() {
		assertEquals(ExifTransform(0, true, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
		assertEquals(ExifTransform(0, false, true), ExifTransform.forOrientation(ExifInterface.ORIENTATION_FLIP_VERTICAL))
	}

	@Test
	fun transposeAndTransverseCombineRotationWithHorizontalFlip() {
		assertEquals(ExifTransform(90, true, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_TRANSPOSE))
		assertEquals(ExifTransform(270, true, false), ExifTransform.forOrientation(ExifInterface.ORIENTATION_TRANSVERSE))
	}

	@Test
	fun nonNormalOrientationsAreNotIdentity() {
		assertFalse(ExifTransform.forOrientation(ExifInterface.ORIENTATION_ROTATE_90).isIdentity)
		assertFalse(ExifTransform.forOrientation(ExifInterface.ORIENTATION_FLIP_VERTICAL).isIdentity)
		assertFalse(ExifTransform.forOrientation(ExifInterface.ORIENTATION_TRANSVERSE).isIdentity)
	}
}
