package org.cryptomator.domain;

import org.cryptomator.util.Optional;

import java.util.Date;

public interface CloudFile extends CloudNode {

	Optional<Long> getSize();

	Optional<Date> getModified();

}
