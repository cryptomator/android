package org.cryptomator.data.db.sqlmapping

import org.mockito.ArgumentMatchers.argThat as defaultArgThat
import org.mockito.Mockito.`when` as whenCalled
import org.mockito.kotlin.any as reifiedAny
import org.mockito.kotlin.anyOrNull as reifiedAnyOrNull
import org.mockito.kotlin.argThat as reifiedArgThat
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatcher
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import java.util.stream.Stream
import kotlin.streams.asStream


class MappingSupportSQLiteDatabaseTest {

	private lateinit var delegateMock: SupportSQLiteDatabase

	private lateinit var identityMapping: MappingSupportSQLiteDatabase
	private lateinit var commentMapping: MappingSupportSQLiteDatabase

	@BeforeEach
	fun beforeEach() {
		delegateMock = mock(SupportSQLiteDatabase::class.java)

		identityMapping = MappingSupportSQLiteDatabase(delegateMock, object : SQLMappingFunction {
			override fun map(sql: String): String = sql
			override fun mapWhereClause(whereClause: String?): String? = whereClause
			override fun mapCursor(cursor: Cursor): Cursor = cursor
		})
		commentMapping = MappingSupportSQLiteDatabase(delegateMock, object : SQLMappingFunction {
			override fun map(sql: String): String = "$sql -- Comment!"
			override fun mapWhereClause(whereClause: String?): String = map(whereClause ?: "1 = 1")
			override fun mapCursor(cursor: Cursor): Cursor = cursor //TODO
		})
	}

	@Test
	fun testExecSQL() {
		identityMapping.execSQL("INSERT INTO `id_test` (`col`) VALUES ('test1')")
		commentMapping.execSQL("INSERT INTO `comment_test` (`col`) VALUES ('test2')")

		verify(delegateMock).execSQL("INSERT INTO `id_test` (`col`) VALUES ('test1')")
		verify(delegateMock).execSQL("INSERT INTO `comment_test` (`col`) VALUES ('test2') -- Comment!")
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testExecSQLWithBindings() {
		identityMapping.execSQL("INSERT INTO `id_test` (`col`) VALUES (?)", arrayOf("test1"))
		commentMapping.execSQL("INSERT INTO `comment_test` (`col`) VALUES (?)", arrayOf("test2"))

		verify(delegateMock).execSQL("INSERT INTO `id_test` (`col`) VALUES (?)", arrayOf("test1"))
		verify(delegateMock).execSQL("INSERT INTO `comment_test` (`col`) VALUES (?) -- Comment!", arrayOf("test2"))
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testQueryString() {
		whenCalled(delegateMock.query(anyString())).thenReturn(DUMMY_CURSOR)

		identityMapping.query("SELECT `col` FROM `id_test`")
		commentMapping.query("SELECT `col` FROM `comment_test`")

		verify(delegateMock).query("SELECT `col` FROM `id_test`")
		verify(delegateMock).query("SELECT `col` FROM `comment_test` -- Comment!")
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testQueryStringWithBindings() {
		whenCalled(delegateMock.query(anyString(), anyArray())).thenReturn(DUMMY_CURSOR)

		identityMapping.query("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1"))
		commentMapping.query("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2"))

		verify(delegateMock).query("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1"))
		verify(delegateMock).query("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2"))
		verifyNoMoreInteractions(delegateMock)
	}


	@Test
	fun testQueryBindable() {
		whenCalled(delegateMock.query(reifiedAny<SupportSQLiteQuery>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `id_test`"))
		commentMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `comment_test`"))

		verify(delegateMock).query(
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `id_test`"), supportSQLiteQueryProperties)
		)
		verify(delegateMock).query(
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` -- Comment!"), supportSQLiteQueryProperties)
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testQueryBindableWithBindings() {
		whenCalled(delegateMock.query(reifiedAny<SupportSQLiteQuery>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")))
		commentMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2")))

		verify(delegateMock).query(
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")), supportSQLiteQueryProperties)
		)
		verify(delegateMock).query(
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2")), supportSQLiteQueryProperties)
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestQueryCancelable")
	fun testQueryCancelable(queries: CallData<SupportSQLiteQuery>, signals: CallData<CancellationSignal?>) {
		whenCalled(delegateMock.query(reifiedAny<SupportSQLiteQuery>(), reifiedAnyOrNull<CancellationSignal>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(queries.idCall, signals.idCall)
		commentMapping.query(queries.commentCall, signals.commentCall)

		verify(delegateMock).query(
			anyPseudoEquals(queries.idExpected, supportSQLiteQueryProperties),
			anyPseudoEqualsUnlessNull(signals.idExpected, setOf(CancellationSignal::isCanceled))
		)
		verify(delegateMock).query(
			anyPseudoEquals(queries.commentExpected, supportSQLiteQueryProperties),
			anyPseudoEqualsUnlessNull(signals.commentExpected, setOf(CancellationSignal::isCanceled))
		)
		verifyNoMoreInteractions(delegateMock)
	}

	/* TODO insert */

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestUpdate")
	fun testUpdate(contentValues: CallData<ContentValues>, whereClauses: CallData<String?>, whereArgs: CallData<List<String>?>) {
		identityMapping.update("id_test", 1001, contentValues.idCall, whereClauses.idCall, whereArgs.idCall?.toTypedArray())
		commentMapping.update("comment_test", 1002, contentValues.commentCall, whereClauses.commentCall, whereArgs.commentCall?.toTypedArray())

		verify(delegateMock).update(eq("id_test"), eq(1001), anyPseudoEquals(contentValues.idExpected, contentValuesProperties), eq(whereClauses.idExpected), eq(whereArgs.idExpected?.toTypedArray()))
		verify(delegateMock).update(eq("comment_test"), eq(1002), anyPseudoEquals(contentValues.commentExpected, contentValuesProperties), eq(whereClauses.commentExpected), eq(whereArgs.commentExpected?.toTypedArray()))
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testDelete() {
		identityMapping.delete("id_test", "`col` = 'test1'", null)
		commentMapping.delete("comment_test", "`col` = 'test2'", null)

		verify(delegateMock).delete("id_test", "`col` = 'test1'", null)
		verify(delegateMock).delete("comment_test", "`col` = 'test2' -- Comment!", null)
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testDeleteWithBindings() {
		identityMapping.delete("id_test", "`col` = ?", arrayOf("test1"))
		commentMapping.delete("comment_test", "`col` = ?", arrayOf("test2"))

		verify(delegateMock).delete("id_test", "`col` = ?", arrayOf("test1"))
		verify(delegateMock).delete("comment_test", "`col` = ? -- Comment!", arrayOf("test2"))
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testDeleteNull() {
		identityMapping.delete("id_test", null, null)
		commentMapping.delete("comment_test", null, null)

		verify(delegateMock).delete("id_test", null, null)
		verify(delegateMock).delete("comment_test", "1 = 1 -- Comment!", null)
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testDeleteNullWithBindings() {
		identityMapping.delete("id_test", null, arrayOf("included but not used id"))
		commentMapping.delete("comment_test", null, arrayOf("included but not used comment"))

		verify(delegateMock).delete("id_test", null, arrayOf("included but not used id"))
		verify(delegateMock).delete("comment_test", "1 = 1 -- Comment!", arrayOf("included but not used comment"))
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testExecPerConnectionSQL() {
		identityMapping.execPerConnectionSQL("INSERT INTO `id_test` (`col`) VALUES (?)", arrayOf("test1"))
		commentMapping.execPerConnectionSQL("INSERT INTO `comment_test` (`col`) VALUES (?)", arrayOf("test2"))

		verify(delegateMock).execPerConnectionSQL("INSERT INTO `id_test` (`col`) VALUES (?)", arrayOf("test1"))
		verify(delegateMock).execPerConnectionSQL("INSERT INTO `comment_test` (`col`) VALUES (?) -- Comment!", arrayOf("test2"))
		verifyNoMoreInteractions(delegateMock)
	}

	/* TODO compileStatement */
}

private inline fun <reified T : Any> anyPseudoEqualsUnlessNull(other: T?, valueExtractors: Set<(T) -> Any?>): T? {
	return if (other != null) defaultArgThat(NullHandlingMatcher(pseudoEquals(other, valueExtractors), false)) else isNull()
}

private inline fun <reified T : Any> anyPseudoEquals(other: T, valueExtractors: Set<(T) -> Any?>): T {
	return reifiedArgThat(pseudoEquals(other, valueExtractors))
}

private fun <T : Any> pseudoEquals(other: T, valueExtractors: Set<(T) -> Any?>): ArgumentMatcher<T> {
	require(valueExtractors.isNotEmpty())
	return PseudoEqualsMatcher(other, valueExtractors)
}

private class PseudoEqualsMatcher<T : Any>(
	private val other: T,
	private val valueExtractors: Set<(T) -> Any?>
) : ArgumentMatcher<T> {

	override fun matches(argument: T): Boolean {
		if (argument === other) {
			return true
		}
		return valueExtractors.all { extractor -> extractor(argument) == extractor(other) }
	}
}

private class NullHandlingMatcher<T>(
	private val delegate: ArgumentMatcher<T>,
	private val matchNull: Boolean
) : ArgumentMatcher<T?> {

	override fun matches(argument: T?): Boolean {
		if (argument == null) {
			return matchNull
		}
		return delegate.matches(argument)
	}
}

private val supportSQLiteQueryProperties
	get() = setOf(SupportSQLiteQuery::sql, SupportSQLiteQuery::argCount, { query: SupportSQLiteQuery ->
		CachingSupportSQLiteProgram().also { query.bindTo(it) }.bindings
	})

private class CachingSupportSQLiteProgram : SupportSQLiteProgram {

	val bindings = mutableMapOf<Int, Any?>()
	override fun bindBlob(index: Int, value: ByteArray) {
		bindings[index] = value
	}

	override fun bindDouble(index: Int, value: Double) {
		bindings[index] = value
	}

	override fun bindLong(index: Int, value: Long) {
		bindings[index] = value
	}

	override fun bindNull(index: Int) {
		bindings[index] = Unit
	}

	override fun bindString(index: Int, value: String) {
		bindings[index] = value
	}

	override fun clearBindings() {
		bindings.clear()
	}

	override fun close() = throw UnsupportedOperationException("Stub!")
}

private val contentValuesProperties
	get() = setOf(
		ContentValues::valueSet
	)

private val DUMMY_CURSOR: Cursor
	get() = MatrixCursor(arrayOf())

private fun mockCancellationSignal(isCanceled: Boolean): CancellationSignal {
	val mock = mock(CancellationSignal::class.java)
	whenCalled(mock.isCanceled).thenReturn(isCanceled)
	whenCalled(mock.toString()).thenReturn("Mock<CancellationSignal>(isCanceled=$isCanceled)")
	return mock
}

private fun mockContentValues(vararg elements: Pair<String, Any?>): ContentValues {
	val entries = mapOf(*elements)
	val mock = mock(ContentValues::class.java)
	whenCalled(mock.valueSet()).thenReturn(entries.entries)
	whenCalled(mock.toString()).thenReturn("Mock<ContentValues>${entries}")
	return mock
}

data class CallData<T>(
	val idCall: T,
	val commentCall: T,
	val idExpected: T,
	val commentExpected: T
)

fun sourceForTestQueryCancelable(): Stream<Arguments> {
	val queries = sequenceOf(
		CallData(
			SimpleSQLiteQuery("SELECT `col` FROM `id_test`"),
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test`"),
			SimpleSQLiteQuery("SELECT `col` FROM `id_test`"),
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` -- Comment!")
		),
		CallData(
			SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")),
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2")),
			SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")),
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2"))
		)
	)
	val signals = listOf<CallData<CancellationSignal?>>(
		CallData(
			mockCancellationSignal(true),
			mockCancellationSignal(false),
			mockCancellationSignal(true),
			mockCancellationSignal(false)
		),
		CallData(
			null,
			null,
			null,
			null
		)
	)

	return queries.cartesianProduct(signals).map { it.toList() }.toArgumentsStream()
}

fun sourceForTestUpdate(): Stream<Arguments> {
	val contentValues = sequenceOf(
		CallData(
			mockContentValues("key1" to "value1"),
			mockContentValues("key2" to "value2"),
			mockContentValues("key1" to "value1"),
			mockContentValues("key2" to "value2")
		),
		CallData(
			mockContentValues("key1" to null),
			mockContentValues(),
			mockContentValues("key1" to null),
			mockContentValues()
		)
	)
	val whereClauses = listOf<CallData<String?>>(
		CallData(
			"`col1` = ?",
			"`col2` = ?",
			"`col1` = ?",
			"`col2` = ? -- Comment!"
		),
		CallData(
			null,
			null,
			null,
			"1 = 1 -- Comment!"
		)
	)
	val whereArgs = listOf<CallData<List<String>?>>( //Use List instead of Array to make result data more readable
		CallData(
			listOf(),
			null,
			listOf(),
			null
		),
		CallData(
			listOf("val1"),
			listOf("val2"),
			listOf("val1"),
			listOf("val2")
		)
	)

	return contentValues.cartesianProduct(whereClauses).cartesianProduct(whereArgs).map { it.toList() }.toArgumentsStream()
}

@JvmName("cartesianProductTwo")
fun <A, B> Sequence<A>.cartesianProduct(other: Iterable<B>): Sequence<Pair<A, B>> = flatMap { a ->
	other.asSequence().map { b -> a to b }
}

@JvmName("cartesianProductThree")
fun <A, B, C> Sequence<Pair<A, B>>.cartesianProduct(other: Iterable<C>): Sequence<Triple<A, B, C>> = flatMap { abPair ->
	other.asSequence().map { c -> Triple(abPair.first, abPair.second, c) }
}

fun Sequence<List<Any?>>.toArgumentsStream(): Stream<Arguments> = map {
	Arguments { it.toTypedArray() }
}.asStream()