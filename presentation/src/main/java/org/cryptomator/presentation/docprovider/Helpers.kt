package org.cryptomator.presentation.docprovider

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).awaitComponent() }

internal fun MatrixCursor.setNotificationUriForId(cr: ContentResolver, documentId: String): Uri {
	return DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY, documentId).also { setNotificationUri(cr, it) }
}
