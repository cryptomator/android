package org.cryptomator.presentation.logging

import java.util.Locale

internal object GeneratedErrorCode {

	private const val A_PRIME = Int.MAX_VALUE

	fun of(e: Throwable): String {
		return format(originCode(rootCause(e))) + ':' + format(traceCode(e))
	}

	private fun rootCause(e: Throwable): Throwable? {
		return if (e.cause == null) {
			e
		} else {
			e.cause
		}
	}

	private fun format(code: Int): String {
		var value = code
		value = value and 0xfffff xor (value ushr 20)
		value = value or 0x100000
		return value.toString(32).substring(1).toUpperCase(Locale.getDefault())
	}

	private fun traceCode(throwable: Throwable?): Int {
		var e: Throwable? = throwable
		var result = -0x596764e6
		while (e != null) {
			result = result * A_PRIME + originCode(e)
			e.stackTrace.forEach { element ->
				result = result * A_PRIME + element.className.hashCode()
				result = result * A_PRIME + element.methodName.hashCode()
			}
			e = e.cause
		}
		return result
	}

	private fun originCode(e: Throwable?, stack: Array<StackTraceElement>? = e?.stackTrace): Int {
		var result = 0x6c528c4a
		result = result * A_PRIME + e?.javaClass?.name.hashCode()
		if (stack?.isNotEmpty() == true) {
			result = result * A_PRIME + stack[0].className.hashCode()
			result = result * A_PRIME + stack[0].methodName.hashCode()
		}
		return result
	}
}
