/*
 * Parts of this file have been copied from the "timber" project by Jake Wharton, hosted on GitHub.
 * The pertaining file can be found here: https://github.com/JakeWharton/timber/blob/9954d94abbaea9d003243be5b69f8ae0ffc0c99d/timber/src/main/java/timber/log/Timber.kt
 *
 * Copied elements include:
 * - The head of all contained functions in the "NamedTimberLogger" class, but not the body
 * - The first line of documentation of all contained functions in the "NamedTimberLogger" class
 *
 * The following notice ONLY applies to that particular source code. Unless stated otherwise,
 * any other source code in this file or in this project is subject to the license of the project, which can be found in the "License" section of this project's README.
 *
 * ---
 *
 * Copyright 2013 Jake Wharton
 * Use of this source code is governed by the Apache 2.0 license that can be found at http://www.apache.org/licenses/LICENSE-2.0
 *
 * THIS FILE WAS CHANGED BY THE LICENSEE, I.E. IT CONTAINS CHANGES NOT MADE BY THE ORIGINAL CONTRIBUTORS LISTED ABOVE.
 */
package org.cryptomator.presentation.docprovider

import org.jetbrains.annotations.NonNls
import timber.log.Timber

fun Timber.Forest.named(tag: String): NamedTimberLogger {
	return NamedTimberLogger(tag)
}

/**
 * This class is a facade for the [companion object of Timber][Timber.Forest]
 * that emits each message with this classes [tag] parameter.
 */
class NamedTimberLogger(private val tag: String) {

	/** Log a verbose message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.v] with it.
	 */
	fun v(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.v(message, *args)
	}

	/** Log a verbose exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.v] with it.
	 */
	fun v(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.v(t, message, *args)
	}

	/** Log a verbose exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.v] with it.
	 */
	fun v(t: Throwable?) {
		Timber.tag(tag)
		Timber.v(t)
	}

	/** Log a debug message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.d] with it.
	 */
	fun d(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.d(message, *args)
	}

	/** Log a debug exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.d] with it.
	 */
	fun d(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.d(t, message, *args)
	}

	/** Log a debug exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.d] with it.
	 */
	fun d(t: Throwable?) {
		Timber.tag(tag)
		Timber.d(t)
	}

	/** Log an info message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.i] with it.
	 */
	fun i(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.i(message, *args)
	}

	/** Log an info exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.i] with it.
	 */
	fun i(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.i(t, message, *args)
	}

	/** Log an info exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.i] with it.
	 */
	fun i(t: Throwable?) {
		Timber.tag(tag)
		Timber.i(t)
	}

	/** Log a warning message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.w] with it.
	 */
	fun w(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.w(message, *args)
	}

	/** Log a warning exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.w] with it.
	 */
	fun w(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.w(t, message, *args)
	}

	/** Log a warning exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.w] with it.
	 */
	fun w(t: Throwable?) {
		Timber.tag(tag)
		Timber.w(t)
	}

	/** Log an error message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.e] with it.
	 */
	fun e(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.e(message, *args)
	}

	/** Log an error exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.e] with it.
	 */
	fun e(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.e(t, message, *args)
	}

	/** Log an error exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.e] with it.
	 */
	fun e(t: Throwable?) {
		Timber.tag(tag)
		Timber.e(t)
	}

	/** Log an assert message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.wtf] with it.
	 */
	fun wtf(@NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.wtf(message, *args)
	}

	/** Log an assert exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.wtf] with it.
	 */
	fun wtf(t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.wtf(t, message, *args)
	}

	/** Log an assert exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.wtf] with it.
	 */
	fun wtf(t: Throwable?) {
		Timber.tag(tag)
		Timber.wtf(t)
	}

	/** Log at `priority` a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.log] with it.
	 */
	fun log(priority: Int, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.log(priority, message, *args)
	}

	/** Log at `priority` an exception and a message with optional format args.
	 *
	 * This method uses this classes [tag] and calls [Timber.log] with it.
	 */
	fun log(priority: Int, t: Throwable?, @NonNls message: String?, vararg args: Any?) {
		Timber.tag(tag)
		Timber.log(priority, t, message, *args)
	}

	/** Log at `priority` an exception.
	 *
	 * This method uses this classes [tag] and calls [Timber.log] with it.
	 */
	fun log(priority: Int, t: Throwable?) {
		Timber.tag(tag)
		Timber.log(priority, t)
	}
}