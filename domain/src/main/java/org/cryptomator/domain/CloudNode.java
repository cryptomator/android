package org.cryptomator.domain;

import java.io.Serializable;

public interface CloudNode extends Serializable {

	Cloud getCloud();

	String getName();

	String getPath();

	CloudFolder getParent();
}
