package org.cryptomator.data.testing

import org.mockito.ArgumentMatchers.argThat as defaultArgThat
import org.mockito.kotlin.argThat as reifiedArgThat
import org.mockito.ArgumentMatcher
import org.mockito.kotlin.isNull

internal inline fun <reified T : Any> anyPseudoEqualsUnlessNull(other: T?, valueExtractors: Set<ValueExtractor<T>>): T? {
	return if (other != null) defaultArgThat(NullHandlingMatcher(pseudoEquals(other, valueExtractors), false)) else isNull()
}

internal inline fun <reified T : Any> anyPseudoEquals(other: T, valueExtractors: Set<ValueExtractor<T>>): T {
	return reifiedArgThat(pseudoEquals(other, valueExtractors))
}

internal fun <T : Any> pseudoEquals(other: T, valueExtractors: Set<ValueExtractor<T>>): ArgumentMatcher<T> {
	require(valueExtractors.isNotEmpty())
	return PseudoEqualsMatcher(other, valueExtractors)
}

internal class PseudoEqualsMatcher<T : Any>( //
	private val other: T, //
	private val valueExtractors: Set<ValueExtractor<T>> //
) : ArgumentMatcher<T> {

	override fun matches(argument: T): Boolean {
		if (argument === other) {
			return true
		}
		return valueExtractors.all { extractor -> extractor(argument) == extractor(other) }
	}
}

internal typealias ValueExtractor<T> = (T) -> Any?

private data class CacheKey<T>(val wrappedKey: T) {

	override fun hashCode(): Int {
		return if (isPrimitive(wrappedKey)) {
			wrappedKey!!.hashCode()
		} else {
			System.identityHashCode(wrappedKey)
		}
	}

	override fun equals(other: Any?): Boolean {
		if (other == null || other !is CacheKey<*>) {
			return false
		}
		return if (isPrimitive(this.wrappedKey) && isPrimitive(other.wrappedKey)) {
			this.wrappedKey == other.wrappedKey
		} else {
			this.wrappedKey === other.wrappedKey
		}
	}
}

private data class CacheValue(val wrappedValue: Any?) //Allows correct handling of nulls

private fun isPrimitive(obj: Any?): Boolean {
	return when (obj) {
		is Boolean, Char, Byte, Short, Int, Long, Float, Double -> true
		else -> false
	}
}

internal fun <T : Any> ValueExtractor<T>.asCached(): ValueExtractor<T> {
	val cache = mutableMapOf<CacheKey<T>, CacheValue>()
	return {
		cache.computeIfAbsent(CacheKey(it)) { key -> CacheValue(this@asCached(key.wrappedKey)) }.wrappedValue
	}
}

internal class NullHandlingMatcher<T>( //
	private val delegate: ArgumentMatcher<T>, //
	private val matchNull: Boolean //
) : ArgumentMatcher<T?> {

	override fun matches(argument: T?): Boolean {
		if (argument == null) {
			return matchNull
		}
		return delegate.matches(argument)
	}
}