package org.cryptomator.presentation.docprovider

import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).component } //Needs to be initialized after onCreate has finished //TODO Verify