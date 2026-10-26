package org.cryptomator.domain;

public interface MicrosoftGraphCloud extends Cloud {

	String accessToken();

	String username();

	String driveId();
}
