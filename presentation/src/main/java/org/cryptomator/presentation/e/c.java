package org.cryptomator.presentation.e;

import java.io.Serializable;
import java.util.Set;

/**
 * This file is the obfuscated AutoUploadFilesStore of Cryptomator in version 1.5.10
 * and is used to recover it in version 1.5.11 and 1.5.11-beta2
 * <p>
 * TODO Delete as soon as possible
 * <p>
 * See more information: https://github.com/cryptomator/android/issues/250
 */

public final class c implements Serializable {

	public static final a Qb = new a();
	private static final long serialVersionUID = -2190476748996271234L;

	private final Set<String> vlb;

	public c(Set<String> paramSet) {
		this.vlb = paramSet;
	}

	public boolean equals(Object paramObject) {
		if (this != paramObject) {
			if (paramObject instanceof c) {
				Object paramObject2 = ((c) paramObject).vlb;
				return (this.vlb == null) ? ((paramObject2 == null)) : this.vlb.equals(paramObject2);
			}
			return false;
		}
		return true;
	}

	public int hashCode() {
		Set<String> set = this.vlb;
		return (set != null) ? set.hashCode() : 0;
	}

	public final Set<String> mE() {
		return this.vlb;
	}

	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("AutoUploadFilesStore(uris=");
		stringBuilder.append(this.vlb);
		stringBuilder.append(")");
		return stringBuilder.toString();
	}

	public static final class a {

		private a() {
		}
	}
}
