package org.cryptomator.data.cloud.okhttplogging;

import java.util.HashSet;
import java.util.Set;

class HeaderNames {

	private final Set<String> lowercaseNames = new HashSet<>();

	public HeaderNames(String... headerNames) {
		for (String headerName : headerNames) {
			lowercaseNames.add(headerName.toLowerCase());
		}
	}

	public boolean contains(String headerName) {
		return lowercaseNames.contains(headerName.toLowerCase());
	}

}
