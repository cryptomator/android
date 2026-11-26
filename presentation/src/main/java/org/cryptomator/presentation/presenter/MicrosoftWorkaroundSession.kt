package org.cryptomator.presentation.presenter

import java.io.File
import java.io.Serializable

/**
 * The copy in shared storage that a Microsoft app edits, and the hash of what it held when the app got it.
 */
class MicrosoftWorkaroundSession(val copy: File, val hashWhenOpened: ByteArray) : Serializable
