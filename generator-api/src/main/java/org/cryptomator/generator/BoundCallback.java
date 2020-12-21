package org.cryptomator.generator;

import java.io.Serializable;

public abstract class BoundCallback<A, R> implements Serializable {

	private final Class<A> declaringType;
	private final Class<R> resultType;
	private final Serializable[] additionalParameters;

	protected BoundCallback(Class<A> declaringType, Class<R> resultType, Serializable... additionalParameters) {
		this.declaringType = declaringType;
		this.resultType = resultType;
		this.additionalParameters = additionalParameters;
	}

	public boolean acceptsNonOkResults() {
		return false;
	}

	public final Class<A> getDeclaringType() {
		return declaringType;
	}

	public final Class<R> getResultType() {
		return resultType;
	}

	public final void call(A instance, R result) {
		Object[] parametersWithResult = new Object[additionalParameters.length + 1];
		parametersWithResult[0] = result;
		System.arraycopy(additionalParameters, 0, parametersWithResult, 1, additionalParameters.length);
		doCall(instance, parametersWithResult);
	}

	protected abstract void doCall(A instance, Object[] parameters);

}
