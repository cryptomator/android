package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
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