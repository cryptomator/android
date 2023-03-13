package org.cryptomator.generator.model;

import org.cryptomator.generator.Callback;
import org.cryptomator.generator.ProcessorException;
import org.cryptomator.generator.utils.Method;
import org.cryptomator.generator.utils.MethodParameter;
import org.cryptomator.generator.utils.Type;

import java.util.List;
import java.util.stream.Collectors;

public class CallbackModel implements Comparable<CallbackModel> {

	private final String callbacksClassName;
	private final String name;
	private final String declaringTypeName;
	private final String resultTypeName;
	private final List<String> additionalParameterTypes;
	private final boolean dispatchResultOkOnly;

	public CallbackModel(Method method) {
		this.declaringTypeName = declaringTypeName(method);
		this.dispatchResultOkOnly = method.getAnnotation(Callback.class).dispatchResultOkOnly();
		this.name = method.name();
		this.resultTypeName = resultTypeName(method);
		this.additionalParameterTypes = additionalParameterTypes(method);
		this.callbacksClassName = callbacksClassName(method);
	}

	private String declaringTypeName(Method method) {
		if (method.declaringType().isAssignableTo("android.app.Activity") || method.declaringType().isAssignableTo("android.app.Fragment")) {
			throw new ProcessorException("@Callback is not allowed in subtypes of Activity or Fragment", method.element());
		}
		return method.declaringType().qualifiedName();
	}

	private String callbacksClassName(Method method) {
		if (resultTypeName.endsWith(".ActivityResult")) {
			return method.declaringType().packageName() + ".ActivityResultCallbacks";
		} else if (resultTypeName.endsWith(".PermissionsResult")) {
			return method.declaringType().packageName() + ".PermissionsResultCallbacks";
		} else {
			return method.declaringType().packageName() + ".SerializableResultCallbacks";
		}
	}

	private String resultTypeName(Method method) {
		if (method.parameters().count() < 1) {
			throw new ProcessorException("Callback method must have at least one parameter", method.element());
		}
		String result = method.parameters().findFirst().get().getType().qualifiedName();
		if (result.equals("org.cryptomator.presentation.workflow.ActivityResult") //
				|| result.equals("org.cryptomator.presentation.workflow.PermissionsResult") //
				|| result.equals("org.cryptomator.presentation.workflow.SerializableResult")) {
			return result;
		}
		throw new ProcessorException("Type of first parameter of callback method must be either ActivityResult, PermissionsResult or SerializableResult", method.element());
	}

	private List<String> additionalParameterTypes(Method method) {
		return method.parameters().skip(1) //
				.peek(this::assertIsSerializable).map(MethodParameter::getType) //
				.map(Type::qualifiedName).collect(Collectors.toList());
	}

	private void assertIsSerializable(MethodParameter methodParameter) {
		if (!methodParameter.getType().isAssignableTo("java.io.Serializable")) {
			throw new ProcessorException("Parameters of callback method must be Serializable", methodParameter.element());
		}
	}

	public String getCallbacksClassName() {
		return callbacksClassName;
	}

	public String getName() {
		return name;
	}

	public String getDeclaringTypeName() {
		return declaringTypeName;
	}

	public String getResultTypeName() {
		return resultTypeName;
	}

	public List<String> getAdditionalParameterTypes() {
		return additionalParameterTypes;
	}

	public boolean isDispatchResultOkOnly() {
		return dispatchResultOkOnly;
	}

	@Override
	public int compareTo(CallbackModel callbackModel) {
		return this.name.compareTo(callbackModel.name);
	}
}
