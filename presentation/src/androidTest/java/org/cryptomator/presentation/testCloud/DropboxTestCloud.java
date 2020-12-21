package org.cryptomator.presentation.testCloud;

import android.content.Context;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.presentation.di.component.ApplicationComponent;
import org.cryptomator.util.crypto.CredentialCryptor;

public class DropboxTestCloud extends TestCloud {
	private final Context context;

	public DropboxTestCloud(Context context) {
		this.context = context;
	}

	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		return WebDavCloud.aWebDavCloudCloud() //
				.withUrl("https://webdav.mc.gmx.net") //
				.withUsername("jraufelder@gmx.de") //
				.withPassword(CredentialCryptor.getInstance(context).encrypt("mG7!3B3Mx")) //
				.build();
	}

	@Override
	public String toString() {
		return "DropboxTestCloud";
	}
}
