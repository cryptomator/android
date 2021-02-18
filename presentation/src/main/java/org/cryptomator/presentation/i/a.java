package org.cryptomator.presentation.i;

import java.io.Serializable;
import java.util.Set;

/**
 * This file is the obfuscated AutoUploadFilesStore of Cryptomator in version 1.5.11-beta1
 * and is used to recover it in version 1.5.11-beta2
 * <p>
 * TODO Delete as soon as possible
 * <p>
 * See more information: https://github.com/cryptomator/android/issues/250
 */

public final class a implements Serializable {

	private static final long serialVersionUID = 5147365921479820025L;
	private final Set<String> b;

	public a(Set<String> paramSet) {
		this.b = paramSet;
	}

	public final Set<String> b() {
		return this.b;
	}

	public boolean equals(Object paramObject) {
		if (this != paramObject) {
			if (paramObject instanceof a) {
				Object paramObject2 = ((a) paramObject).b;
				return (this.b == null) ? ((paramObject2 == null)) : this.b.equals(paramObject2);
			}
			return false;
		}
		return true;
	}

	public int hashCode() {
		int bool;
		Set<String> set = this.b;
		if (set != null) {
			bool = set.hashCode();
		} else {
			bool = 0;
		}
		return bool;
	}

	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("AutoUploadFilesStore(uris=");
		stringBuilder.append(this.b);
		stringBuilder.append(")");
		return stringBuilder.toString();
	}
}
