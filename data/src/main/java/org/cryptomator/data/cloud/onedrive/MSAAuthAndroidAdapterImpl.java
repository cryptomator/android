package org.cryptomator.data.cloud.onedrive;

import android.content.Context;

import org.cryptomator.data.BuildConfig;
import org.cryptomator.data.cloud.onedrive.graph.MSAAuthAndroidAdapter;

public class MSAAuthAndroidAdapterImpl extends MSAAuthAndroidAdapter {

	private static final String[] SCOPES = new String[] {"https://graph.microsoft.com/Files.ReadWrite", "offline_access", "openid"};

	public MSAAuthAndroidAdapterImpl(Context context, String refreshToken) {
		super(context, refreshToken);
	}

	@Override
	public String getClientId() {
		return BuildConfig.ONEDRIVE_API_KEY;
	}

	@Override
	public String[] getScopes() {
		return SCOPES;
	}
}
