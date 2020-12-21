package org.cryptomator.domain;

import java.io.Serializable;

public interface Cloud extends Serializable {

	Long id();

	CloudType type();

	boolean configurationMatches(Cloud cloud);

	boolean predefined();

	boolean persistent();

	boolean requiresNetwork();
}
