package org.cryptomator.data.testing

import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

fun <A, B> Sequence<A>.cartesianProductTwo(other: Iterable<B>): Sequence<Pair<A, B>> = flatMap { a ->
	other.asSequence().map { b -> a to b }
}

fun <A, B, C> Sequence<Pair<A, B>>.cartesianProductThree(other: Iterable<C>): Sequence<Triple<A, B, C>> = flatMap { abPair ->
	other.asSequence().map { c -> Triple(abPair.first, abPair.second, c) }
}

fun <T> Sequence<Triple<T, T, T>>.cartesianProductFour(other: Iterable<T>): Sequence<List<T>> = flatMap { triple ->
	other.asSequence().map { otherElement -> listOf(triple.first, triple.second, triple.third, otherElement) }
}

fun Sequence<List<Any?>>.toArgumentsStream(): Stream<Arguments> = map {
	Arguments { it.toTypedArray() }
}.asStream()

fun Iterable<Any?>?.nullCount(): Int = this?.count { it == null } ?: 0

inline fun <reified T> Iterable<Any?>?.argCount(): Int = this?.asSequence()?.filterIsInstance<T>()?.count() ?: 0

fun Iterable<Any?>?.toBindingsMap(): Map<Int, Any?> {
	return this?.asSequence() //
		?.map { if (it is Int) it.toLong() else it } // Required because java.lang.Integer.valueOf(x) != java.lang.Long.valueOf(x)
		?.mapIndexed { index, value -> index + 1 to value } //
		?.toMap() ?: emptyMap()
}