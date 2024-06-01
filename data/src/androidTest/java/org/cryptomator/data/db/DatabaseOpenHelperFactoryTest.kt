package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Field
import java.lang.reflect.Method

@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabaseOpenHelperFactoryTest {

	@Test //For org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabase
	fun verifySupportSQLiteDatabase() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteDatabase.beginTransaction(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.beginTransactionNonExclusive(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.beginTransactionWithListener(android.database.sqlite.SQLiteTransactionListener): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.beginTransactionWithListenerNonExclusive(android.database.sqlite.SQLiteTransactionListener): void",
			"java.lang.AutoCloseable.close(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.compileStatement(java.lang.String): androidx.sqlite.db.SupportSQLiteStatement",
			"androidx.sqlite.db.SupportSQLiteDatabase.delete(java.lang.String, java.lang.String, [Ljava.lang.Object;): int",
			"androidx.sqlite.db.SupportSQLiteDatabase.disableWriteAheadLogging(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.enableWriteAheadLogging(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.endTransaction(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.execPerConnectionSQL(java.lang.String, [Ljava.lang.Object;): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.execSQL(java.lang.String): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.execSQL(java.lang.String, [Ljava.lang.Object;): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.getAttachedDbs(): java.util.List",
			"androidx.sqlite.db.SupportSQLiteDatabase.getMaximumSize(): long",
			"androidx.sqlite.db.SupportSQLiteDatabase.getPageSize(): long",
			"androidx.sqlite.db.SupportSQLiteDatabase.getPath(): java.lang.String",
			"androidx.sqlite.db.SupportSQLiteDatabase.getVersion(): int",
			"androidx.sqlite.db.SupportSQLiteDatabase.inTransaction(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.insert(java.lang.String, int, android.content.ContentValues): long",
			"androidx.sqlite.db.SupportSQLiteDatabase.isDatabaseIntegrityOk(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.isDbLockedByCurrentThread(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.isExecPerConnectionSQLSupported(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.isOpen(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.isReadOnly(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.isWriteAheadLoggingEnabled(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.needUpgrade(int): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.query(androidx.sqlite.db.SupportSQLiteQuery): android.database.Cursor",
			"androidx.sqlite.db.SupportSQLiteDatabase.query(java.lang.String): android.database.Cursor",
			"androidx.sqlite.db.SupportSQLiteDatabase.query(androidx.sqlite.db.SupportSQLiteQuery, android.os.CancellationSignal): android.database.Cursor",
			"androidx.sqlite.db.SupportSQLiteDatabase.query(java.lang.String, [Ljava.lang.Object;): android.database.Cursor",
			"androidx.sqlite.db.SupportSQLiteDatabase.setForeignKeyConstraintsEnabled(boolean): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.setLocale(java.util.Locale): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.setMaxSqlCacheSize(int): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.setMaximumSize(long): long",
			"androidx.sqlite.db.SupportSQLiteDatabase.setPageSize(long): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.setTransactionSuccessful(): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.setVersion(int): void",
			"androidx.sqlite.db.SupportSQLiteDatabase.update(java.lang.String, int, android.content.ContentValues, java.lang.String, [Ljava.lang.Object;): int",
			"androidx.sqlite.db.SupportSQLiteDatabase.yieldIfContendedSafely(): boolean",
			"androidx.sqlite.db.SupportSQLiteDatabase.yieldIfContendedSafely(long): boolean"
		)
		assertEquals(bom, SupportSQLiteDatabase::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabase.MappingSupportSQLiteStatement
	fun verifySupportSQLiteStatement() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteProgram.bindBlob(int, [B): void",
			"androidx.sqlite.db.SupportSQLiteProgram.bindDouble(int, double): void",
			"androidx.sqlite.db.SupportSQLiteProgram.bindLong(int, long): void",
			"androidx.sqlite.db.SupportSQLiteProgram.bindNull(int): void",
			"androidx.sqlite.db.SupportSQLiteProgram.bindString(int, java.lang.String): void",
			"androidx.sqlite.db.SupportSQLiteProgram.clearBindings(): void",
			"java.lang.AutoCloseable.close(): void",
			"androidx.sqlite.db.SupportSQLiteStatement.execute(): void",
			"androidx.sqlite.db.SupportSQLiteStatement.executeInsert(): long",
			"androidx.sqlite.db.SupportSQLiteStatement.executeUpdateDelete(): int",
			"androidx.sqlite.db.SupportSQLiteStatement.simpleQueryForLong(): long",
			"androidx.sqlite.db.SupportSQLiteStatement.simpleQueryForString(): java.lang.String"
		)
		assertEquals(bom, SupportSQLiteStatement::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteDatabase.MappingSupportSQLiteQuery
	fun verifySupportSQLiteQuery() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteQuery.bindTo(androidx.sqlite.db.SupportSQLiteProgram): void",
			"androidx.sqlite.db.SupportSQLiteQuery.getArgCount(): int",
			"androidx.sqlite.db.SupportSQLiteQuery.getSql(): java.lang.String"
		)
		assertEquals(bom, SupportSQLiteQuery::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.sqlmapping.MappingSupportSQLiteOpenHelper
	fun verifySupportSQLiteOpenHelper() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteOpenHelper.close(): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper.getDatabaseName(): java.lang.String",
			"androidx.sqlite.db.SupportSQLiteOpenHelper.getReadableDatabase(): androidx.sqlite.db.SupportSQLiteDatabase",
			"androidx.sqlite.db.SupportSQLiteOpenHelper.getWritableDatabase(): androidx.sqlite.db.SupportSQLiteDatabase",
			"androidx.sqlite.db.SupportSQLiteOpenHelper.setWriteAheadLoggingEnabled(boolean): void"
		)
		assertEquals(bom, SupportSQLiteOpenHelper::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.PatchedCallback
	fun verifySupportSQLiteOpenHelperCallback() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.version: int",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onConfigure(androidx.sqlite.db.SupportSQLiteDatabase): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onCorruption(androidx.sqlite.db.SupportSQLiteDatabase): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onCreate(androidx.sqlite.db.SupportSQLiteDatabase): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onDowngrade(androidx.sqlite.db.SupportSQLiteDatabase, int, int): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onOpen(androidx.sqlite.db.SupportSQLiteDatabase): void",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.onUpgrade(androidx.sqlite.db.SupportSQLiteDatabase, int, int): void",
			//
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Callback.Companion: androidx.sqlite.db.SupportSQLiteOpenHelper#Callback#Companion"
		)
		assertEquals(bom, SupportSQLiteOpenHelper.Callback::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.DatabaseOpenHelperFactoryKt.patchConfiguration
	fun verifySupportSQLiteOpenHelperConfiguration() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.allowDataLossOnRecovery: boolean",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.callback: androidx.sqlite.db.SupportSQLiteOpenHelper#Callback",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.context: android.content.Context",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.name: java.lang.String",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.useNoBackupDirectory: boolean",
			//
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.Companion: androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Companion",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration.builder(android.content.Context): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder"
		)
		assertEquals(bom, SupportSQLiteOpenHelper.Configuration::class.java.getFieldAndMethodNames())
	}

	@Test //For org.cryptomator.data.db.DatabaseOpenHelperFactoryKt.patchConfiguration
	fun verifySupportSQLiteOpenHelperConfigurationBuilder() {
		val bom = setOf(
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder.allowDataLossOnRecovery(boolean): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder.callback(androidx.sqlite.db.SupportSQLiteOpenHelper#Callback): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder.name(java.lang.String): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder",
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder.noBackupDirectory(boolean): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder",
			//
			"androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration#Builder.build(): androidx.sqlite.db.SupportSQLiteOpenHelper#Configuration"
		)
		assertEquals(bom, SupportSQLiteOpenHelper.Configuration.Builder::class.java.getFieldAndMethodNames())
	}
}

private val DEFAULT_MEMBERS = setOf(
	"java.lang.Object.equals(java.lang.Object): boolean",
	"java.lang.Object.getClass(): java.lang.Class",
	"java.lang.Object.hashCode(): int",
	"java.lang.Object.notify(): void",
	"java.lang.Object.notifyAll(): void",
	"java.lang.Object.toString(): java.lang.String",
	"java.lang.Object.wait(): void",
	"java.lang.Object.wait(long): void",
	"java.lang.Object.wait(long, int): void"
)

private fun Class<*>.getFieldAndMethodNames(): Set<String> {
	val fieldsSequence = fields.asSequence().map { it.signature }
	val methodsSequence = methods.asSequence().map { it.signature }
	return (fieldsSequence + methodsSequence - DEFAULT_MEMBERS).toSet()
}

private val Field.signature: String
	get() = "${declaringClass.name}.$name: ${type.name}".replace('$', '#')

private val Method.signature: String
	get() {
		val parameters = parameterTypes.asSequence().map { it.name }.joinToString(", ")
		return "${declaringClass.name}.$name($parameters): ${returnType.name}".replace('$', '#')
	}