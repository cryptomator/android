package org.cryptomator.data.db.sqlmapping

import org.mockito.ArgumentMatchers.argThat as defaultArgThat
import org.mockito.Mockito.`when` as whenCalled
import org.mockito.kotlin.any as reifiedAny
import org.mockito.kotlin.anyOrNull as reifiedAnyOrNull
import org.mockito.kotlin.argThat as reifiedArgThat
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabase.MappingSupportSQLiteStatement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.internal.verification.VerificationModeFactory.times
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.stubbing.OngoingStubbing
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

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
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

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
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

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
		verify(delegateMock).query(
			anyPseudoEquals(queries.idExpected, supportSQLiteQueryProperties),
			anyPseudoEqualsUnlessNull(signals.idExpected, setOf<ValueExtractor<CancellationSignal>>(CancellationSignal::isCanceled))
		)
		verify(delegateMock).query(
			anyPseudoEquals(queries.commentExpected, supportSQLiteQueryProperties),
			anyPseudoEqualsUnlessNull(signals.commentExpected, setOf<ValueExtractor<CancellationSignal>>(CancellationSignal::isCanceled))
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestInsert")
	fun testInsert(arguments: CallDataTwo<ContentValues, String>) {
		val (idCompiledStatement: SupportSQLiteStatement, idBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()
		val (commentCompiledStatement: SupportSQLiteStatement, commentBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()

		whenCalled(delegateMock.compileStatement(arguments.idExpected)).thenReturn(idCompiledStatement)
		whenCalled(delegateMock.compileStatement(arguments.commentExpected)).thenReturn(commentCompiledStatement)

		val order = inOrder(delegateMock, idCompiledStatement, commentCompiledStatement)
		identityMapping.insert("id_test", 1, arguments.idCall)

		order.verify(delegateMock).compileStatement(arguments.idExpected)
		order.verify(idCompiledStatement, times(arguments.idCall.argCount<String>())).bindString(anyInt(), anyString())
		order.verify(idCompiledStatement, times(arguments.idCall.nullCount())).bindNull(anyInt())
		order.verify(idCompiledStatement, times(arguments.idCall.argCount<Int>())).bindLong(anyInt(), anyLong())
		order.verify(idCompiledStatement).executeInsert()
		order.verify(idCompiledStatement).close()
		verifyNoMoreInteractions(idCompiledStatement)

		order.verifyNoMoreInteractions()
		commentMapping.insert("comment_test", 2, arguments.commentCall)

		order.verify(delegateMock).compileStatement(arguments.commentExpected)
		/* */ verifyNoMoreInteractions(delegateMock)
		order.verify(commentCompiledStatement, times(arguments.commentCall.argCount<String>())).bindString(anyInt(), anyString())
		order.verify(commentCompiledStatement, times(arguments.commentCall.nullCount())).bindNull(anyInt())
		order.verify(commentCompiledStatement, times(arguments.commentCall.argCount<Int>())).bindLong(anyInt(), anyLong())
		order.verify(commentCompiledStatement).executeInsert()
		order.verify(commentCompiledStatement).close()
		verifyNoMoreInteractions(commentCompiledStatement)

		order.verifyNoMoreInteractions()

		assertEquals(arguments.idCall.toBindingsMap(), idBindings)
		assertEquals(arguments.commentCall.toBindingsMap(), commentBindings)
	}

	@Test
	fun testInsertEmptyValues() {
		val emptyContentValues = mockContentValues()

		assertThrows<SQLException> { identityMapping.insert("id_test", 1, emptyContentValues) }
		assertThrows<SQLException> { commentMapping.insert("comment_test", 2, emptyContentValues) }

		verifyNoInteractions(delegateMock)
	}

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestInsertConflictAlgorithms")
	fun testInsertConflictAlgorithms(arguments: Triple<Int, String, String>) {
		val (conflictAlgorithm: Int, idStatement: String, commentStatement: String) = arguments

		val idCompiledStatement = mock(SupportSQLiteStatement::class.java)
		val commentCompiledStatement = mock(SupportSQLiteStatement::class.java)

		whenCalled(delegateMock.compileStatement(idStatement)).thenReturn(idCompiledStatement)
		whenCalled(delegateMock.compileStatement(commentStatement)).thenReturn(commentCompiledStatement)

		val order = inOrder(delegateMock, idCompiledStatement, commentCompiledStatement)

		val idContentValues = mockContentValues("col1" to "val1") //Inlining this declaration causes problems for some reason
		assertDoesNotThrow { identityMapping.insert("id_test", conflictAlgorithm, idContentValues) }

		order.verify(delegateMock).compileStatement(idStatement)
		order.verify(idCompiledStatement).bindString(1, "val1")
		order.verify(idCompiledStatement).executeInsert()
		order.verify(idCompiledStatement).close()
		verifyNoMoreInteractions(idCompiledStatement)

		order.verifyNoMoreInteractions()
		val commentContentValues = mockContentValues("col2" to "val2")
		assertDoesNotThrow { commentMapping.insert("comment_test", conflictAlgorithm, commentContentValues) }

		order.verify(delegateMock).compileStatement(commentStatement)
		/* */ verifyNoMoreInteractions(delegateMock)
		order.verify(commentCompiledStatement).bindString(1, "val2")
		order.verify(commentCompiledStatement).executeInsert()
		order.verify(commentCompiledStatement).close()
		verifyNoMoreInteractions(commentCompiledStatement)

		order.verifyNoMoreInteractions()
	}

	@Test
	fun testInsertInvalidConflictAlgorithms() {
		val idContentValues = mockContentValues("col1" to "val1") //Inlining this declaration causes problems for some reason
		val commentContentValues = mockContentValues("col2" to "val2")

		assertThrows<SQLException> { identityMapping.insert("id_test", -1, idContentValues) }
		assertThrows<SQLException> { commentMapping.insert("comment_test", -1, commentContentValues) }

		assertThrows<SQLException> { identityMapping.insert("id_test", 6, idContentValues) }
		assertThrows<SQLException> { commentMapping.insert("comment_test", 6, commentContentValues) }

		verifyNoInteractions(delegateMock)
	}

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

	@Test
	fun testCompileStatement() {
		whenCalled(delegateMock.isOpen).thenReturn(true)

		val idSql = "INSERT INTO `id_test` (`col1`) VALUES ('val1')"
		val commentSql = "INSERT INTO `comment_test` (`col2`) VALUES ('val2')"

		val order = inOrder(delegateMock)
		order.verifyNoMoreInteractions()

		val idStatement1 = identityMapping.compileStatement(idSql)
		order.verify(delegateMock).isOpen
		order.verifyNoMoreInteractions()
		val idStatement2 = identityMapping.compileStatement(idSql)
		val commentStatement1 = commentMapping.compileStatement(commentSql)
		val commentStatement2 = commentMapping.compileStatement(commentSql)

		order.verify(delegateMock, times(3)).isOpen
		order.verifyNoMoreInteractions()

		assertInstanceOf(MappingSupportSQLiteStatement::class.java, idStatement1)
		assertInstanceOf(MappingSupportSQLiteStatement::class.java, idStatement2)
		assertInstanceOf(MappingSupportSQLiteStatement::class.java, commentStatement1)
		assertInstanceOf(MappingSupportSQLiteStatement::class.java, commentStatement2)

		assertNotSame(idStatement1, idStatement2)
		assertNotSame(commentStatement1, commentStatement2)
	}
}

private inline fun <reified T : Any> anyPseudoEqualsUnlessNull(other: T?, valueExtractors: Set<ValueExtractor<T>>): T? {
	return if (other != null) defaultArgThat(NullHandlingMatcher(pseudoEquals(other, valueExtractors), false)) else isNull()
}

private inline fun <reified T : Any> anyPseudoEquals(other: T, valueExtractors: Set<ValueExtractor<T>>): T {
	return reifiedArgThat(pseudoEquals(other, valueExtractors))
}

private fun <T : Any> pseudoEquals(other: T, valueExtractors: Set<ValueExtractor<T>>): ArgumentMatcher<T> {
	require(valueExtractors.isNotEmpty())
	return PseudoEqualsMatcher(other, valueExtractors)
}

private class PseudoEqualsMatcher<T : Any>(
	private val other: T,
	private val valueExtractors: Set<ValueExtractor<T>>
) : ArgumentMatcher<T> {

	override fun matches(argument: T): Boolean {
		if (argument === other) {
			return true
		}
		return valueExtractors.all { extractor -> extractor(argument) == extractor(other) }
	}
}

private typealias ValueExtractor<T> = (T) -> Any?

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

private fun <T : Any> ValueExtractor<T>.asCached(): ValueExtractor<T> {
	val cache = mutableMapOf<CacheKey<T>, CacheValue>()
	return {
		cache.computeIfAbsent(CacheKey(it)) { key -> CacheValue(this@asCached(key.wrappedKey)) }.wrappedValue
	}
}

private inline fun <T> OngoingStubbing<T>.thenDo(crossinline action: (invocation: InvocationOnMock) -> Unit): OngoingStubbing<T> = thenAnswer { action(it) }

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

private fun newCachedSupportSQLiteQueryProperties(): Set<ValueExtractor<SupportSQLiteQuery>> = setOf(
	SupportSQLiteQuery::sql.asCached(),
	SupportSQLiteQuery::argCount,
	{ query: SupportSQLiteQuery ->
		CachingSupportSQLiteProgram().also { query.bindTo(it) }.bindings
	}.asCached()
)

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

private val contentValuesProperties: Set<ValueExtractor<ContentValues>>
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

private fun mockSupportSQLiteStatement(): Pair<SupportSQLiteStatement, Map<Int, Any?>> {
	val bindings: MutableMap<Int, Any?> = mutableMapOf()
	val mock = mock(SupportSQLiteStatement::class.java)
	whenCalled(mock.bindString(anyInt(), anyString())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = it.getArgument(1, String::class.java)
	}
	whenCalled(mock.bindLong(anyInt(), anyLong())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = it.getArgument(1, java.lang.Long::class.java)
	}
	whenCalled(mock.bindNull(anyInt())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = null
	}
	return mock to bindings
}

private fun mockContentValues(vararg elements: Pair<String, Any?>): ContentValues {
	return mockContentValues(mapOf(*elements))
}

private fun mockContentValues(entries: Map<String, Any?>): ContentValues {
	val mock = mock(ContentValues::class.java)
	whenCalled(mock.valueSet()).thenReturn(entries.entries)
	whenCalled(mock.size()).thenReturn(entries.size)
	whenCalled(mock.isEmpty).thenReturn(entries.isEmpty())
	whenCalled(mock.keySet()).thenReturn(entries.keys)
	whenCalled(mock.get(anyString())).then {
		entries[it.getArgument(0, String::class.java)]
	}
	whenCalled(mock.toString()).thenReturn("Mock<ContentValues>${entries}")
	return mock
}

data class CallData<T>(
	val idCall: T,
	val commentCall: T,
	val idExpected: T,
	val commentExpected: T
)

data class CallDataTwo<C, E>(
	val idCall: C,
	val commentCall: C,
	val idExpected: E,
	val commentExpected: E
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

fun sourceForTestInsert(): Stream<CallDataTwo<ContentValues, String>> = sequenceOf(
	//The ContentValues in this dataset always have the following order and counts:
	//String [0,2], null[0,1], Int[0,1]
	//This makes the ordered verification a lot easier
	CallDataTwo(
		mockContentValues("key1" to null),
		mockContentValues("key2" to null),
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)",
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1" to "value1"),
		mockContentValues("key2" to "value2"),
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)",
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1-1" to "value1-1", "key1-2" to "value1-2"),
		mockContentValues("key2-1" to "value2-1", "key2-2" to "value2-2"),
		"INSERT OR ROLLBACK  INTO id_test(key1-1,key1-2) VALUES (?,?)",
		"INSERT OR ABORT  INTO comment_test(key2-1,key2-2) VALUES (?,?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1" to "value1", "intKey1" to 10101),
		mockContentValues("key2" to "value2", "intKey2" to 20202),
		"INSERT OR ROLLBACK  INTO id_test(key1,intKey1) VALUES (?,?)",
		"INSERT OR ABORT  INTO comment_test(key2,intKey2) VALUES (?,?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1" to "value1", "nullKey1" to null),
		mockContentValues("key2" to "value2", "nullKey2" to null),
		"INSERT OR ROLLBACK  INTO id_test(key1,nullKey1) VALUES (?,?)",
		"INSERT OR ABORT  INTO comment_test(key2,nullKey2) VALUES (?,?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1" to "value1", "nullKey1" to null, "intKey1" to 10101),
		mockContentValues("key2" to "value2"),
		"INSERT OR ROLLBACK  INTO id_test(key1,nullKey1,intKey1) VALUES (?,?,?)",
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!"
	),
	CallDataTwo(
		mockContentValues("key1" to "value1"),
		mockContentValues("key2" to "value2", "nullKey2" to null, "intKey2" to 20202),
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)",
		"INSERT OR ABORT  INTO comment_test(key2,nullKey2,intKey2) VALUES (?,?,?) -- Comment!"
	)
).asStream()

fun sourceForTestInsertConflictAlgorithms(): Stream<Triple<Int, String, String>> = sequenceOf(
	Triple(
		SQLiteDatabase.CONFLICT_NONE,
		"INSERT INTO id_test(col1) VALUES (?)",
		"INSERT INTO comment_test(col2) VALUES (?) -- Comment!"
	),
	Triple(
		SQLiteDatabase.CONFLICT_ROLLBACK,
		"INSERT OR ROLLBACK  INTO id_test(col1) VALUES (?)",
		"INSERT OR ROLLBACK  INTO comment_test(col2) VALUES (?) -- Comment!"
	),
	Triple(
		SQLiteDatabase.CONFLICT_ABORT,
		"INSERT OR ABORT  INTO id_test(col1) VALUES (?)",
		"INSERT OR ABORT  INTO comment_test(col2) VALUES (?) -- Comment!"
	),
	Triple(
		SQLiteDatabase.CONFLICT_FAIL,
		"INSERT OR FAIL  INTO id_test(col1) VALUES (?)",
		"INSERT OR FAIL  INTO comment_test(col2) VALUES (?) -- Comment!"
	),
	Triple(
		SQLiteDatabase.CONFLICT_IGNORE,
		"INSERT OR IGNORE  INTO id_test(col1) VALUES (?)",
		"INSERT OR IGNORE  INTO comment_test(col2) VALUES (?) -- Comment!"
	),
	Triple(
		SQLiteDatabase.CONFLICT_REPLACE,
		"INSERT OR REPLACE  INTO id_test(col1) VALUES (?)",
		"INSERT OR REPLACE  INTO comment_test(col2) VALUES (?) -- Comment!"
	),
).asStream()

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


private fun ContentValues.nullCount(): Int = valueSet().count { it.value == null }

private inline fun <reified T> ContentValues.argCount(): Int = valueSet().asSequence().map { it.value }.filterIsInstance<T>().count()

private fun ContentValues.toBindingsMap(): Map<Int, Any?> {
	return valueSet().map { it.value } //
		.map { if (it is Int) it.toLong() else it } // Required because java.lang.Integer.valueOf(x) != java.lang.Long.valueOf(x)
		.mapIndexed { index, value -> index + 1 to value } //
		.toMap()
}