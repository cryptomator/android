package org.cryptomator.presentation.presenter

import android.os.Bundle
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.Serializable

/**
 * A [Bundle] keeping the booleans, ints and serializables that [InstanceStates] saves, so that a test can recreate a
 * presenter. Values of other types are lost.
 */
fun instanceStateBundle(): Bundle {
	val values = HashMap<String, Any?>()
	val bundle = mock<Bundle>()
	doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putBoolean(any(), any())
	doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putInt(any(), any())
	doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putSerializable(any(), anyOrNull())
	whenever(bundle.getBoolean(any())).thenAnswer { values[it.getArgument(0)] as Boolean? ?: false }
	whenever(bundle.getInt(any())).thenAnswer { values[it.getArgument(0)] as Int? ?: 0 }
	whenever(bundle.getSerializable(any())).thenAnswer { values[it.getArgument(0)] as Serializable? }
	return bundle
}
