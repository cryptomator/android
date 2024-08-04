package org.cryptomator.data.db.sqlmapping

import org.mockito.kotlin.any as reifiedAny
import org.mockito.kotlin.anyOrNull as reifiedAnyOrNull
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
import org.cryptomator.data.db.sqlmapping.Mapping.COMMENT
import org.cryptomator.data.db.sqlmapping.Mapping.COUNTER
import org.cryptomator.data.db.sqlmapping.Mapping.IDENTITY
import org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabase.MappingSupportSQLiteStatement
import org.cryptomator.data.testing.ValueExtractor
import org.cryptomator.data.testing.anyPseudoEquals
import org.cryptomator.data.testing.anyPseudoEqualsUnlessNull
import org.cryptomator.data.testing.asCached
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.internal.verification.VerificationModeFactory.times
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.KInOrder
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer
import org.mockito.stubbing.OngoingStubbing
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
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
		whenever(delegateMock.query(anyString())).thenReturn(DUMMY_CURSOR)

		identityMapping.query("SELECT `col` FROM `id_test`")
		commentMapping.query("SELECT `col` FROM `comment_test`")

		verify(delegateMock).query("SELECT `col` FROM `id_test`")
		verify(delegateMock).query("SELECT `col` FROM `comment_test` -- Comment!")
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testQueryStringWithBindings() {
		whenever(delegateMock.query(anyString(), anyArray())).thenReturn(DUMMY_CURSOR)

		identityMapping.query("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1"))
		commentMapping.query("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2"))

		verify(delegateMock).query("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1"))
		verify(delegateMock).query("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2"))
		verifyNoMoreInteractions(delegateMock)
	}


	@Test
	fun testQueryBindable() {
		whenever(delegateMock.query(reifiedAny<SupportSQLiteQuery>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `id_test`"))
		commentMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `comment_test`"))

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
		verify(delegateMock).query( //
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `id_test`"), supportSQLiteQueryProperties)
		)
		verify(delegateMock).query( //
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` -- Comment!"), supportSQLiteQueryProperties)
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@Test
	fun testQueryBindableWithBindings() {
		whenever(delegateMock.query(reifiedAny<SupportSQLiteQuery>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")))
		commentMapping.query(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2")))

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
		verify(delegateMock).query( //
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")), supportSQLiteQueryProperties)
		)
		verify(delegateMock).query( //
			anyPseudoEquals(SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2")), supportSQLiteQueryProperties)
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestQueryCancelable")
	fun testQueryCancelable(queries: CallData<SupportSQLiteQuery>, signals: CallData<CancellationSignal?>) {
		whenever(delegateMock.query(reifiedAny<SupportSQLiteQuery>(), reifiedAnyOrNull<CancellationSignal>())).thenReturn(DUMMY_CURSOR)

		identityMapping.query(queries.idCall, signals.idCall)
		commentMapping.query(queries.commentCall, signals.commentCall)

		val supportSQLiteQueryProperties = newCachedSupportSQLiteQueryProperties()
		verify(delegateMock).query( //
			anyPseudoEquals(queries.idExpected, supportSQLiteQueryProperties), //
			anyPseudoEqualsUnlessNull(signals.idExpected, setOf<ValueExtractor<CancellationSignal>>(CancellationSignal::isCanceled))
		)
		verify(delegateMock).query( //
			anyPseudoEquals(queries.commentExpected, supportSQLiteQueryProperties), //
			anyPseudoEqualsUnlessNull(signals.commentExpected, setOf<ValueExtractor<CancellationSignal>>(CancellationSignal::isCanceled))
		)
		verifyNoMoreInteractions(delegateMock)
	}

	@ParameterizedTest
	@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestInsert")
	fun testInsert(arguments: CallDataTwo<ContentValues, String>) {
		val (idCompiledStatement: SupportSQLiteStatement, idBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()
		val (commentCompiledStatement: SupportSQLiteStatement, commentBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()

		whenever(delegateMock.compileStatement(arguments.idExpected)).thenReturn(idCompiledStatement)
		whenever(delegateMock.compileStatement(arguments.commentExpected)).thenReturn(commentCompiledStatement)

		val order = inOrder(delegateMock, idCompiledStatement, commentCompiledStatement)

		testSingleInsert(order, { identityMapping.insert("id_test", 1, arguments.idCall) }, idCompiledStatement, arguments.idExpected, arguments.idCall, idBindings, false)
		testSingleInsert(order, { commentMapping.insert("comment_test", 2, arguments.commentCall) }, commentCompiledStatement, arguments.commentExpected, arguments.commentCall, commentBindings, true)

		order.verifyNoMoreInteractions()
	}

	private fun testSingleInsert( //
		order: KInOrder, //
		toVerify: () -> Unit, //
		compiledStatement: SupportSQLiteStatement, //
		expected: String, //
		values: ContentValues, //
		bindings: Map<Int, Any?>, //
		lastTest: Boolean //
	) {
		val valueSet: Set<Map.Entry<String?, Any?>?> = values.valueSet()
		val alsoVerify = {
			order.verify(compiledStatement).executeInsert()
			order.verify(compiledStatement).close()
		}
		testSingleCompiledStatement(order, toVerify, compiledStatement, expected, valueSet.asSequence().requireNoNulls().map { it.value }.toList(), bindings, alsoVerify, lastTest)
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
		val idContentValues = mockContentValues("col1" to "val1") //Inlining this declaration causes problems for some reason
		val commentContentValues = mockContentValues("col2" to "val2")

		val (idCompiledStatement: SupportSQLiteStatement, idBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()
		val (commentCompiledStatement: SupportSQLiteStatement, commentBindings: Map<Int, Any?>) = mockSupportSQLiteStatement()

		whenever(delegateMock.compileStatement(idStatement)).thenReturn(idCompiledStatement)
		whenever(delegateMock.compileStatement(commentStatement)).thenReturn(commentCompiledStatement)

		val order = inOrder(delegateMock, idCompiledStatement, commentCompiledStatement)

		testSingleInsert( //
			order, { assertDoesNotThrow { identityMapping.insert("id_test", conflictAlgorithm, idContentValues) } }, idCompiledStatement, idStatement, idContentValues, idBindings, false //
		)
		testSingleInsert( //
			order, { assertDoesNotThrow { commentMapping.insert("comment_test", conflictAlgorithm, commentContentValues) } }, commentCompiledStatement, commentStatement, commentContentValues, commentBindings, true //
		)

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
		whenever(delegateMock.isOpen).thenReturn(true)

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

	@Nested
	inner class MappingSupportSQLiteStatementTest {

		private lateinit var counter: AtomicInteger
		private lateinit var counterMapping: MappingSupportSQLiteDatabase

		@BeforeEach
		fun beforeEachInner() { //Don't shadow "beforeEach" of outer class
			counter = AtomicInteger(0)
			counterMapping = MappingSupportSQLiteDatabase(delegateMock, object : SQLMappingFunction {
				override fun map(sql: String): String = "$sql -- ${counter.getAndIncrement()}!"
				override fun mapWhereClause(whereClause: String?): String = map(whereClause ?: "1 = 1")
				override fun mapCursor(cursor: Cursor): Cursor = cursor
			})
		}

		private fun resolveMapping(mapping: Mapping) = when (mapping) {
			IDENTITY -> identityMapping
			COMMENT -> commentMapping
			COUNTER -> counterMapping
		}

		@ParameterizedTest
		@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestNewBoundStatementSingle")
		fun testNewBoundStatementSingle(statementData: Triple<Mapping, String, List<String>>, values: List<Any?>?) {
			val (mapping: MappingSupportSQLiteDatabase, call: String, expected: List<String>) = statementData.resolve(::resolveMapping)
			val expectedSize = expected.size
			require(expectedSize >= 1)

			val mappingStatement = mapping.createAndBindStatement(call, values)
			testConsecutiveNewBoundStatements(List(expectedSize) { statementData }, List(expectedSize) { values }) { _: Mapping, _: String, _: List<Any?>? -> mappingStatement }
		}

		@ParameterizedTest
		@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestNewBoundStatementMultiple")
		fun testNewBoundStatementMultiple(statementData: List<Triple<Mapping, String, List<String>>>, values: List<List<Any?>?>) {
			testConsecutiveNewBoundStatements(statementData, values)
		}

		@EnabledIfEnvironmentVariable(named = "RUN_VERY_LARGE_TESTS", matches = "(?i)true|1|yes", disabledReason = "Very large tests are disabled")
		@ParameterizedTest
		@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestNewBoundStatementNumerous1")
		fun testNewBoundStatementNumerous1(statementData: List<Triple<Mapping, String, List<String>>>, values: List<List<Any?>?>) {
			testConsecutiveNewBoundStatements(statementData, values)
		}

		@EnabledIfEnvironmentVariable(named = "RUN_VERY_LARGE_TESTS", matches = "(?i)true|1|yes", disabledReason = "Very large tests are disabled")
		@ParameterizedTest
		@MethodSource("org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabaseTestKt#sourceForTestNewBoundStatementNumerous2")
		fun testNewBoundStatementNumerous2(statementData: List<Triple<Mapping, String, List<String>>>, values: List<List<Any?>?>) {
			testConsecutiveNewBoundStatements(statementData, values)
		}

		private fun testConsecutiveNewBoundStatements(statementData: List<Triple<Mapping, String, List<String>>>, values: List<List<Any?>?>) {
			val mappingStatementSupplier = { mapping: Mapping, callSql: String, boundValues: List<Any?>? ->
				resolveMapping(mapping).createAndBindStatement(callSql, boundValues)
			}
			testConsecutiveNewBoundStatements(statementData, values, mappingStatementSupplier)
		}

		private fun testConsecutiveNewBoundStatements( //
			statementData: List<Triple<Mapping, String, List<String>>>, //
			values: List<List<Any?>?>, //
			mappingStatementSupplier: (Mapping, String, List<Any?>?) -> MappingSupportSQLiteStatement //
		) {
			val statementCount = statementData.size
			require(statementCount > 0)
			require(values.size == statementCount)

			val expected = statementData.fold(emptyList<String>() to 0) { acc, current ->
				val nextExpectedValue = current.third.let { it.getOrNull(acc.second) ?: it.first() }
				require(nextExpectedValue != SENTINEL) {
					"Invalid test data; received sentinel to be added to ${acc.first} from $current @ ${acc.second}"
				}
				val nextExpectedAcc = acc.first + nextExpectedValue
				val nextIndex = if (current.first == COUNTER) acc.second + 1 else acc.second
				nextExpectedAcc to nextIndex
			}.first
			require(expected.size == statementCount)

			val compiledStatements = mutableListOf<SupportSQLiteStatement>()
			val newBoundStatementData = mutableListOf<NewBoundStatementData>()
			for ((index, entry) in statementData.withIndex()) {
				val (compiledStatement: SupportSQLiteStatement, binding: Map<Int, Any?>) = mockSupportSQLiteStatement()
				val mappingStatement = mappingStatementSupplier(entry.first, entry.second, values[index])
				compiledStatements.add(compiledStatement)
				newBoundStatementData.add(NewBoundStatementData(mappingStatement, compiledStatement, expected[index], values[index], binding))
			}

			val order = inOrder(delegateMock, *compiledStatements.toTypedArray())
			order.verifyNoMoreInteractions()
			verifyNoMoreInteractions(delegateMock)

			val results = expected.asSequence().zip(compiledStatements.asSequence()).groupBy({ it.first }) { it.second }
			whenever(delegateMock.compileStatement(reifiedAnyOrNull())).then(throwingInvocationHandler(false, results))

			repeat(statementCount) { index ->
				val data = newBoundStatementData[index]
				testSingleNewBoundStatement(order, data.mappingStatement, data.compiledStatement, data.expected, data.values, data.bindings, index == statementCount - 1)
			}

			order.verifyNoMoreInteractions()
		}
	}

	private fun testSingleNewBoundStatement( //
		order: KInOrder, //
		mappingStatement: MappingSupportSQLiteStatement, //
		compiledStatement: SupportSQLiteStatement, //
		expected: String, //
		values: List<Any?>?, //
		bindings: Map<Int, Any?>, //
		lastTest: Boolean //
	) = testSingleCompiledStatement( //
		order, { assertSame(compiledStatement, mappingStatement.newBoundStatement()) }, compiledStatement, expected, values, bindings, { }, lastTest //
	)

	private fun testSingleCompiledStatement( //
		order: KInOrder, //
		toVerify: () -> Unit, //
		compiledStatement: SupportSQLiteStatement, //
		expected: String, //
		values: List<Any?>?, //
		bindings: Map<Int, Any?>, //
		alsoVerify: () -> Unit, //
		lastTest: Boolean //
	) {
		order.verifyNoMoreInteractions()
		toVerify()

		order.verify(delegateMock).compileStatement(expected)
		if (lastTest) verifyNoMoreInteractions(delegateMock)
		order.verify(compiledStatement, times(values.argCount<String>())).bindString(anyInt(), anyString())
		order.verify(compiledStatement, times(values.nullCount())).bindNull(anyInt())
		order.verify(compiledStatement, times(values.argCount<Int>())).bindLong(anyInt(), anyLong())
		alsoVerify()
		verifyNoMoreInteractions(compiledStatement)

		assertEquals(values.toBindingsMap(), bindings)
	}
}

private data class NewBoundStatementData(
	val mappingStatement: MappingSupportSQLiteStatement, //
	val compiledStatement: SupportSQLiteStatement, //
	val expected: String, //
	val values: List<Any?>?, //
	val bindings: Map<Int, Any?>, //
)

enum class Mapping { IDENTITY, COMMENT, COUNTER }

private const val SENTINEL = "::SENTINEL::"

private inline fun <T> OngoingStubbing<T>.thenDo(crossinline action: (invocation: InvocationOnMock) -> Unit): OngoingStubbing<T> = thenAnswer { action(it) }

private fun newCachedSupportSQLiteQueryProperties(): Set<ValueExtractor<SupportSQLiteQuery>> = setOf( //
	SupportSQLiteQuery::sql.asCached(), //
	SupportSQLiteQuery::argCount, //
	{ query: SupportSQLiteQuery -> //
		CachingSupportSQLiteProgram().also { query.bindTo(it) }.bindings //
	}.asCached() //
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
	get() = setOf( //
		ContentValues::valueSet
	)

private val DUMMY_CURSOR: Cursor
	get() = MatrixCursor(arrayOf())

private fun <T, R> throwingInvocationHandler(retainLast: Boolean, handledResults: Map<T, List<R>>): Answer<R> = object : Answer<R> {
	val values: Map<T, MutableList<R>> = handledResults.asSequence().map { entry ->
		require(entry.value.isNotEmpty())
		entry.key to LinkedList(entry.value)
	}.toMap()

	override fun answer(invocation: InvocationOnMock): R {
		val argument = invocation.getArgument<T>(0)
		val resultsForArg = requireNotNull(values[argument]) { "Undefined invocation $invocation" }
		require(resultsForArg.isNotEmpty()) { "No results for invocation $invocation" }
		return if (resultsForArg.size == 1 && retainLast) resultsForArg.first() else resultsForArg.removeFirst()
	}
}

private fun mockCancellationSignal(isCanceled: Boolean): CancellationSignal {
	val mock = mock(CancellationSignal::class.java)
	whenever(mock.isCanceled).thenReturn(isCanceled)
	whenever(mock.toString()).thenReturn("Mock<CancellationSignal>(isCanceled=$isCanceled)")
	return mock
}

private fun mockSupportSQLiteStatement(): Pair<SupportSQLiteStatement, Map<Int, Any?>> {
	val bindings: MutableMap<Int, Any?> = mutableMapOf()
	val mock = mock(SupportSQLiteStatement::class.java)
	whenever(mock.bindString(anyInt(), anyString())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = it.getArgument(1, String::class.java)
	}
	whenever(mock.bindLong(anyInt(), anyLong())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = it.getArgument(1, java.lang.Long::class.java)
	}
	whenever(mock.bindNull(anyInt())).thenDo {
		bindings[it.getArgument(0, Integer::class.java).toInt()] = null
	}
	return mock to bindings
}

private fun mockContentValues(vararg elements: Pair<String, Any?>): ContentValues {
	return mockContentValues(mapOf(*elements))
}

private fun mockContentValues(entries: Map<String, Any?>): ContentValues {
	val mock = mock(ContentValues::class.java)
	whenever(mock.valueSet()).thenReturn(entries.entries)
	whenever(mock.size()).thenReturn(entries.size)
	whenever(mock.isEmpty).thenReturn(entries.isEmpty())
	whenever(mock.keySet()).thenReturn(entries.keys)
	whenever(mock.get(anyString())).then {
		entries[it.getArgument(0, String::class.java)]
	}
	whenever(mock.toString()).thenReturn("Mock<ContentValues>${entries}")
	return mock
}

private fun MappingSupportSQLiteDatabase.createAndBindStatement(sql: String, values: List<Any?>?): MappingSupportSQLiteStatement {
	val mappingStatement = this.MappingSupportSQLiteStatement(sql)
	SimpleSQLiteQuery.bind(mappingStatement, values?.toTypedArray())
	return mappingStatement
}

data class CallData<T>( //
	val idCall: T, //
	val commentCall: T, //
	val idExpected: T, //
	val commentExpected: T //
)

data class CallDataTwo<C, E>( //
	val idCall: C, //
	val commentCall: C, //
	val idExpected: E, //
	val commentExpected: E //
)

private fun Triple<Mapping, String, List<String>>.resolve(resolver: (Mapping) -> MappingSupportSQLiteDatabase) = Triple(resolver(first), second, third)

fun sourceForTestQueryCancelable(): Stream<Arguments> {
	val queries = sequenceOf( //
		CallData( //
			SimpleSQLiteQuery("SELECT `col` FROM `id_test`"), //
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test`"), //
			SimpleSQLiteQuery("SELECT `col` FROM `id_test`"), //
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` -- Comment!") //
		), CallData( //
			SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")), //
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ?", arrayOf("test2")), //
			SimpleSQLiteQuery("SELECT `col` FROM `id_test` WHERE `col` = ?", arrayOf("test1")), //
			SimpleSQLiteQuery("SELECT `col` FROM `comment_test` WHERE `col` = ? -- Comment!", arrayOf("test2")) //
		)
	)
	val signals = listOf<CallData<CancellationSignal?>>( //
		CallData( //
			mockCancellationSignal(true), //
			mockCancellationSignal(false), //
			mockCancellationSignal(true), //
			mockCancellationSignal(false) //
		), CallData( //
			null, //
			null, //
			null, //
			null //
		)
	)

	return queries.cartesianProductTwo(signals).map { it.toList() }.toArgumentsStream()
}

fun sourceForTestInsert(): Stream<CallDataTwo<ContentValues, String>> = sequenceOf( //
	//The ContentValues in this dataset always have the following order and counts:
	//String [0,2], null[0,1], Int[0,1]
	//This makes the ordered verification a lot easier
	CallDataTwo( //
		mockContentValues("key1" to null), //
		mockContentValues("key2" to null), //
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)", //
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1" to "value1"), //
		mockContentValues("key2" to "value2"), //
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)", //
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1-1" to "value1-1", "key1-2" to "value1-2"), //
		mockContentValues("key2-1" to "value2-1", "key2-2" to "value2-2"), //
		"INSERT OR ROLLBACK  INTO id_test(key1-1,key1-2) VALUES (?,?)", //
		"INSERT OR ABORT  INTO comment_test(key2-1,key2-2) VALUES (?,?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1" to "value1", "intKey1" to 10101), //
		mockContentValues("key2" to "value2", "intKey2" to 20202), //
		"INSERT OR ROLLBACK  INTO id_test(key1,intKey1) VALUES (?,?)", //
		"INSERT OR ABORT  INTO comment_test(key2,intKey2) VALUES (?,?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1" to "value1", "nullKey1" to null), //
		mockContentValues("key2" to "value2", "nullKey2" to null), //
		"INSERT OR ROLLBACK  INTO id_test(key1,nullKey1) VALUES (?,?)", //
		"INSERT OR ABORT  INTO comment_test(key2,nullKey2) VALUES (?,?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1" to "value1", "nullKey1" to null, "intKey1" to 10101), //
		mockContentValues("key2" to "value2"), //
		"INSERT OR ROLLBACK  INTO id_test(key1,nullKey1,intKey1) VALUES (?,?,?)", //
		"INSERT OR ABORT  INTO comment_test(key2) VALUES (?) -- Comment!" //
	), CallDataTwo( //
		mockContentValues("key1" to "value1"), //
		mockContentValues("key2" to "value2", "nullKey2" to null, "intKey2" to 20202), //
		"INSERT OR ROLLBACK  INTO id_test(key1) VALUES (?)", //
		"INSERT OR ABORT  INTO comment_test(key2,nullKey2,intKey2) VALUES (?,?,?) -- Comment!" //
	)
).asStream()

fun sourceForTestInsertConflictAlgorithms(): Stream<Triple<Int, String, String>> = sequenceOf( //
	Triple( //
		SQLiteDatabase.CONFLICT_NONE, //
		"INSERT INTO id_test(col1) VALUES (?)", //
		"INSERT INTO comment_test(col2) VALUES (?) -- Comment!" //
	), Triple( //
		SQLiteDatabase.CONFLICT_ROLLBACK, //
		"INSERT OR ROLLBACK  INTO id_test(col1) VALUES (?)", //
		"INSERT OR ROLLBACK  INTO comment_test(col2) VALUES (?) -- Comment!" //
	), Triple( //
		SQLiteDatabase.CONFLICT_ABORT, //
		"INSERT OR ABORT  INTO id_test(col1) VALUES (?)", //
		"INSERT OR ABORT  INTO comment_test(col2) VALUES (?) -- Comment!" //
	), Triple( //
		SQLiteDatabase.CONFLICT_FAIL, //
		"INSERT OR FAIL  INTO id_test(col1) VALUES (?)", //
		"INSERT OR FAIL  INTO comment_test(col2) VALUES (?) -- Comment!" //
	), Triple( //
		SQLiteDatabase.CONFLICT_IGNORE, //
		"INSERT OR IGNORE  INTO id_test(col1) VALUES (?)", //
		"INSERT OR IGNORE  INTO comment_test(col2) VALUES (?) -- Comment!" //
	), Triple( //
		SQLiteDatabase.CONFLICT_REPLACE, //
		"INSERT OR REPLACE  INTO id_test(col1) VALUES (?)", //
		"INSERT OR REPLACE  INTO comment_test(col2) VALUES (?) -- Comment!" //
	)
).asStream()

fun sourceForTestUpdate(): Stream<Arguments> {
	val contentValues = sequenceOf( //
		CallData( //
			mockContentValues("key1" to "value1"), //
			mockContentValues("key2" to "value2"), //
			mockContentValues("key1" to "value1"), //
			mockContentValues("key2" to "value2") //
		), CallData( //
			mockContentValues("key1" to null), //
			mockContentValues(), //
			mockContentValues("key1" to null), //
			mockContentValues() //
		)
	)
	val whereClauses = listOf<CallData<String?>>( //
		CallData( //
			"`col1` = ?", //
			"`col2` = ?", //
			"`col1` = ?", //
			"`col2` = ? -- Comment!" //
		), CallData( //
			null, //
			null, //
			null, //
			"1 = 1 -- Comment!" //
		)
	)
	val whereArgs = listOf<CallData<List<String>?>>( //Use List instead of Array to make result data more readable
		CallData( //
			listOf(), //
			null, //
			listOf(), //
			null //
		), CallData( //
			listOf("val1"), //
			listOf("val2"), //
			listOf("val1"), //
			listOf("val2") //
		)
	)

	return contentValues.cartesianProductTwo(whereClauses).cartesianProductThree(whereArgs).map { it.toList() }.toArgumentsStream()
}

private val newBoundStatementValues = listOf<List<Any?>?>( //
	//The ContentValues in this dataset always have the following order and counts:
	//String [0,2], null[0,1], Int[0,1]
	//This makes the ordered verification a lot easier
	null, //
	listOf(), //
	listOf(null), //
	listOf("value"), //
	listOf("value1", "value2"), //
	listOf("value", 10101), //
	listOf("value", null), //
	listOf("value", null, 10101) //
)

fun sourceForTestNewBoundStatementSingle(): Stream<Arguments> {
	val statementData = sequenceOf( //
		Triple( //
			IDENTITY, "INSERT INTO `id_test` (`id_col`) VALUES (?)", listOf( //
				"INSERT INTO `id_test` (`id_col`) VALUES (?)" //
			)
		), Triple( //
			COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
				"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
			)
		), Triple( //
			COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 1!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 2!" //
			)
		), Triple( //
			IDENTITY, "SELECT count(*) FROM `id_test`", listOf( //
				"SELECT count(*) FROM `id_test`" //
			)
		), Triple( //
			COMMENT, "SELECT count(*) FROM `comment_test`", listOf( //
				"SELECT count(*) FROM `comment_test` -- Comment!" //
			)
		), Triple( //
			COUNTER, "SELECT count(*) FROM `counter_test`", listOf( //
				"SELECT count(*) FROM `counter_test` -- 0!", //
				"SELECT count(*) FROM `counter_test` -- 1!", //
				"SELECT count(*) FROM `counter_test` -- 2!" //
			)
		), Triple( //
			IDENTITY, "DELETE FROM `id_test` WHERE `id_col1` = 'id_value' AND `id_col2` = ?", listOf( //
				"DELETE FROM `id_test` WHERE `id_col1` = 'id_value' AND `id_col2` = ?" //
			)
		), Triple( //
			COMMENT, "DELETE FROM `comment_test` WHERE `comment_col1` = 'comment_value' AND `comment_col2` = ?", listOf( //
				"DELETE FROM `comment_test` WHERE `comment_col1` = 'comment_value' AND `comment_col2` = ? -- Comment!" //
			)
		), Triple( //
			COUNTER, "DELETE FROM `counter_test` WHERE `counter_col1` = 'counter_value' AND `counter_col2` = ?", listOf( //
				"DELETE FROM `counter_test` WHERE `counter_col1` = 'counter_value' AND `counter_col2` = ? -- 0!", //
				"DELETE FROM `counter_test` WHERE `counter_col1` = 'counter_value' AND `counter_col2` = ? -- 1!", //
				"DELETE FROM `counter_test` WHERE `counter_col1` = 'counter_value' AND `counter_col2` = ? -- 2!" //
			)
		)
	)
	return statementData //
		.cartesianProductTwo(newBoundStatementValues) //
		.map { it.toList() } //
		.toArgumentsStream()
}

private val newBoundStatementValuesSmall = listOf<List<Any?>?>( //
	//The ContentValues in this dataset always have the following order and counts:
	//String [0,2], null[0,1], Int[0,1]
	//This makes the ordered verification a lot easier
	null, //
	listOf(), //
	listOf("value"), //
	listOf("value", null, 10101) //
)

fun sourceForTestNewBoundStatementMultiple(): Stream<Arguments> {
	//result.count() == 6 * 18 == 108
	val statementData = listOf( //
		Triple( //
			Triple( //
				IDENTITY, "INSERT INTO `id_test` (`id_col`) VALUES (?)", listOf( //
					"INSERT INTO `id_test` (`id_col`) VALUES (?)" //
				)
			), //
			Triple( //
				COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
					"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
				)
			), //
			Triple( //
				COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
					"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			)
		), //
		Triple( //
			Triple( //
				COMMENT, "INSERT INTO `comment_test1` (`comment_col1`) VALUES (?)", listOf( //
					"INSERT INTO `comment_test1` (`comment_col1`) VALUES (?) -- Comment!" //
				)
			), //
			Triple( //
				COMMENT, "INSERT INTO `comment_test2` (`comment_col2`) VALUES (?)", listOf( //
					"INSERT INTO `comment_test2` (`comment_col2`) VALUES (?) -- Comment!" //
				)
			), //
			Triple( //
				COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
					"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			)
		), //
		Triple( //
			Triple( //
				COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
					"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			), //
			Triple( //
				COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
					"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
				)
			), //
			Triple( //
				COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
					SENTINEL, //
					"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 1!", //
					SENTINEL //
				)
			)
		), //
		Triple( //
			Triple( //
				COUNTER, "INSERT INTO `counter_test1` (`counter_col1`) VALUES (?)", listOf( //
					"INSERT INTO `counter_test1` (`counter_col1`) VALUES (?) -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			), //
			Triple( //
				COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
					"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
				)
			), //
			Triple( //
				COUNTER, "INSERT INTO `counter_test2` (`counter_col2`) VALUES (?)", listOf( //
					SENTINEL, //
					"INSERT INTO `counter_test2` (`counter_col2`) VALUES (?) -- 1!", //
					SENTINEL //
				)
			)
		), //
		Triple( //
			Triple( //
				COUNTER, "DELETE FROM `counter_test`", listOf( //
					"DELETE FROM `counter_test` -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			), //
			Triple( //
				COUNTER, "DELETE FROM `counter_test`", listOf( //
					SENTINEL, //
					"DELETE FROM `counter_test` -- 1!", //
					SENTINEL //
				)
			), //
			Triple( //
				COUNTER, "DELETE FROM `counter_test`", listOf( //
					SENTINEL, //
					SENTINEL, //
					"DELETE FROM `counter_test` -- 2!" //
				)
			)
		), //
		Triple( //
			Triple( //
				COUNTER, "INSERT INTO `counter_test1` (`counter_col1`) VALUES (?)", listOf( //
					"INSERT INTO `counter_test1` (`counter_col1`) VALUES (?) -- 0!", //
					SENTINEL, //
					SENTINEL //
				)
			), //
			Triple( //
				COUNTER, "DELETE FROM `counter_test2`", listOf( //
					SENTINEL, //
					"DELETE FROM `counter_test2` -- 1!", //
					SENTINEL //
				)
			), //
			Triple( //
				COUNTER, "SELECT count(*) FROM `counter_test3` WHERE `counter_col3` = ?", listOf( //
					SENTINEL, //
					SENTINEL, //
					"SELECT count(*) FROM `counter_test3` WHERE `counter_col3` = ? -- 2!" //
				)
			)
		)
	)
	val values = listOf( //
		Triple( //
			null, null, null //
		), Triple( //
			listOf(), listOf(), listOf() //
		), Triple( //
			listOf(null), listOf(null), listOf(null) //
		), Triple( //
			listOf("value"), listOf("value"), listOf("value") //
		), Triple( //
			listOf("value"), listOf(null), listOf("value") //
		), Triple( //
			listOf("value1"), listOf("value2"), listOf("value3") //
		), Triple( //
			listOf("value"), listOf(2_000_0), listOf() //
		), Triple( //
			listOf("value"), listOf(2_000_0), listOf(null) //
		), Triple( //
			listOf("value", "value"), listOf("value"), listOf() //
		), Triple( //
			listOf("value1-1", "value1-2"), listOf("value2-1"), listOf() //
		), Triple( //
			listOf("value1-1", 1_000_2), listOf(null), listOf() //
		), Triple( //
			listOf("value", "value", "value"), listOf("value"), listOf() //
		), Triple( //
			listOf("value", "value", "value"), listOf("value", "value", "value"), listOf("value", "value", "value") //
		), Triple( //
			listOf("value1", "value2", "value3"), listOf("value1", "value2", "value3"), listOf("value1", "value2", "value3") //
		), Triple( //
			listOf("value1", "value1", "value1"), listOf("value2", "value2", "value2"), listOf("value3", "value3", "value3") //
		), Triple( //
			listOf("value1-1", "value1-2", "value1-3"), listOf("value2-1", "value2-2", "value2-3"), listOf("value3-1", "value3-2", "value3-3") //
		), Triple( //
			listOf("value1-1", "value1-2", null), listOf("value2-1", null, null), listOf("value3-1", "value3-2", "value3-3") //
		), Triple( //
			listOf("value1-1", null, 1_000_3), listOf("value2-1", 2_000_2, 2_000_3), listOf(null, null, 3_000_3) //
		)
	)

	val statementDataSets: Sequence<List<Triple<Mapping, String, List<String>>>> = statementData.asSequence() //
		.map { it.toList() }

	val valueSets: List<List<List<Any?>?>> = values.asSequence() //
		.map { it.toList() } //
		.toList()
	val result: Sequence<Pair<List<Triple<Mapping, String, List<String>>>, List<List<Any?>?>>> = statementDataSets.cartesianProductTwo(valueSets)
	return result //
		.map { it.toList() } //
		.toArgumentsStream()
}

fun sourceForTestNewBoundStatementNumerous1(): Stream<Arguments> {
	//result.count() == 3 ^ 3 * 4 ^ 3 == 1,728
	val statementData = listOf( //
		Triple( //
			IDENTITY, "INSERT INTO `id_test` (`id_col`) VALUES (?)", listOf( //
				"INSERT INTO `id_test` (`id_col`) VALUES (?)" //
			)
		), Triple( //
			COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
				"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
			)
		), Triple( //
			COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 1!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 2!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 3!" //
			)
		)
	)

	val statementDataSets: Sequence<List<Triple<Mapping, String, List<String>>>> = statementData.asSequence() //
		.cartesianProductTwo(statementData) //
		.cartesianProductThree(statementData) //
		.map { it.toList() }

	val valueSets: List<List<List<Any?>?>> = newBoundStatementValuesSmall.asSequence() //
		.cartesianProductTwo(newBoundStatementValuesSmall) //
		.cartesianProductThree(newBoundStatementValuesSmall) //
		.map { it.toList() } //
		.toList()
	val result: Sequence<Pair<List<Triple<Mapping, String, List<String>>>, List<List<Any?>?>>> = statementDataSets.cartesianProductTwo(valueSets)
	return result //
		.map { it.toList() } //
		.toArgumentsStream()
}

fun sourceForTestNewBoundStatementNumerous2(): Stream<Arguments> {
	//result.count() == 3 ^ 4 * 8 ^ 4 == 331,776
	val statementData = listOf( //
		Triple( //
			IDENTITY, "INSERT INTO `id_test` (`id_col`) VALUES (?)", listOf( //
				"INSERT INTO `id_test` (`id_col`) VALUES (?)" //
			)
		), Triple( //
			COMMENT, "INSERT INTO `comment_test` (`comment_col`) VALUES (?)", listOf( //
				"INSERT INTO `comment_test` (`comment_col`) VALUES (?) -- Comment!" //
			)
		), Triple( //
			COUNTER, "INSERT INTO `counter_test` (`counter_col`) VALUES (?)", listOf( //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 0!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 1!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 2!", //
				"INSERT INTO `counter_test` (`counter_col`) VALUES (?) -- 3!" //
			)
		)
	)

	val statementDataSets: Sequence<List<Triple<Mapping, String, List<String>>>> = statementData.asSequence() //
		.cartesianProductTwo(statementData) //
		.cartesianProductThree(statementData) //
		.cartesianProductFour(statementData)

	val valueSets: List<List<List<Any?>?>> = newBoundStatementValues.asSequence() //
		.cartesianProductTwo(newBoundStatementValues) //
		.cartesianProductThree(newBoundStatementValues) //
		.cartesianProductFour(newBoundStatementValues) //
		.toList()
	val result: Sequence<Pair<List<Triple<Mapping, String, List<String>>>, List<List<Any?>?>>> = statementDataSets.cartesianProductTwo(valueSets)
	return result //
		.map { it.toList() } //
		.toArgumentsStream()
}

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

private fun Iterable<Any?>?.nullCount(): Int = this?.count { it == null } ?: 0

private inline fun <reified T> Iterable<Any?>?.argCount(): Int = this?.asSequence()?.filterIsInstance<T>()?.count() ?: 0

private fun Iterable<Any?>?.toBindingsMap(): Map<Int, Any?> {
	return this?.asSequence() //
		?.map { if (it is Int) it.toLong() else it } // Required because java.lang.Integer.valueOf(x) != java.lang.Long.valueOf(x)
		?.mapIndexed { index, value -> index + 1 to value } //
		?.toMap() ?: emptyMap()
}