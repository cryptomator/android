package org.cryptomator.domain.usecases;

public interface UpdateCheck {

	String releaseNote();

	String getVersion();

	String getUrlApk();

	String getUrlReleaseNote();
}
