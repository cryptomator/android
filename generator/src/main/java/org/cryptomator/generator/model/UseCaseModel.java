package org.cryptomator.generator.model;

import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.ProcessorException;
import org.cryptomator.generator.utils.Method;
import org.cryptomator.generator.utils.MethodParameter;
import org.cryptomator.generator.utils.Type;

import java.util.Collection;

import static java.util.stream.Collectors.toList;

public class UseCaseModel {

	private final String simpleName;
	private final String className;
	private final String packageName;
	private final String implClassName;
	private final Collection<InjectedModel> injected;
	private final Collection<Object> injectedAndParams;
	private final Collection<ParameterModel> parameters;
	private final boolean resultIsVoid;
	private final String resultClassName;
	private final String subscriberResultClassName;
	private final boolean hasParameters;
	private final boolean hasProgressAware;
	private final boolean hasCancelHandler;
	private final String progressStateName;

	public UseCaseModel(Type type) {
		Method executeMethod = checkExecuteMethod(type);
		Method constructor = checkConstructor(type);

		this.simpleName = simpleName(type);
		this.className = className(type);
		this.packageName = type.packageName();
		this.implClassName = type.qualifiedName();
		this.injected = injected(constructor);
		this.parameters = parameters(constructor);
		this.injectedAndParams = injectedAndParams(constructor);
		this.resultIsVoid = executeMethod.returnsVoid();
		this.resultClassName = resultClassName(executeMethod);
		this.subscriberResultClassName = subscriberResultClassName(executeMethod);
		this.hasParameters = !parameters.isEmpty();
		this.hasProgressAware = executeMethod.parameters().count() == 1;
		this.hasCancelHandler = hasCancelHandler(type);
		this.progressStateName = progressStateName(executeMethod);
	}

	private boolean hasCancelHandler(Type type) {
		return type.methods().filter(method -> "onCancel".equals(method.name())).findFirst().isPresent();
	}

	private String subscriberResultClassName(Method executeMethod) {
		if (executeMethod.returnsVoid()) {
			return "Object";
		} else {
			return executeMethod.getSourceCodeRepresentationOfType();
		}
	}

	private String resultClassName(Method executeMethod) {
		if (executeMethod.returnsVoid()) {
			return "Void";
		} else {
			return executeMethod.getSourceCodeRepresentationOfType();
		}
	}

	private String progressStateName(Method executeMethod) {
		if (executeMethod.parameters().count() == 1) {
			return executeMethod.parameters().findFirst().get().getTypeArgument(0).qualifiedName();
		} else {
			return null;
		}
	}

	private Method checkExecuteMethod(Type type) {
		java.util.Optional<Method> executeMethod = type.methods().filter(method -> "execute".equals(method.name())).findFirst();
		if (!executeMethod.isPresent()) {
			throw new ProcessorException("UseCase must define exactly and only one public method 'execute'", type.element());
		}
		boolean paramsInvalid = true;
		if (executeMethod.get().parameters().count() == 0) {
			paramsInvalid = false;
		} else if (executeMethod.get().parameters().count() == 1) {
			if (executeMethod.get().parameters().findFirst().get().getType().isAssignableFrom("org.cryptomator.domain.usecases.ProgressAware")) {
				paramsInvalid = false;
			}
		}
		if (paramsInvalid) {
			throw new ProcessorException("'execute' method must not have parameters or a single parameter of declaringType ProgressAware", type.element());
		}
		return executeMethod.get();
	}

	private Method checkConstructor(Type type) {
		if (type.constructors().count() != 1) {
			throw new ProcessorException("UseCase must define exactly only one constructor", type.element());
		}
		return type.constructors().findFirst().get();
	}

	private Collection<InjectedModel> injected(Method constructor) {
		return constructor.parameters() //
				.filter(parameter -> !parameter.hasAnnotation(Parameter.class)) //
				.map(InjectedModel::new).collect(toList());
	}

	private Collection<ParameterModel> parameters(Method constructor) {
		return constructor.parameters() //
				.filter(parameter -> parameter.hasAnnotation(Parameter.class)) //
				.map(ParameterModel::new).collect(toList());
	}

	private Collection<Object> injectedAndParams(Method constructor) {
		return constructor.parameters().map(parameter -> {
			if (parameter.hasAnnotation(Parameter.class)) {
				return new ParameterModel(parameter);
			} else {
				return new InjectedModel(parameter);
			}
		}).collect(toList());
	}

	private String simpleName(Type type) {
		return type.simpleName() + "UseCase";
	}

	private String className(Type type) {
		return type.packageName() + "." + simpleName(type);
	}

	public String getClassName() {
		return className;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getImplClassName() {
		return implClassName;
	}

	public Collection<InjectedModel> getInjected() {
		return injected;
	}

	public Collection<ParameterModel> getParameters() {
		return parameters;
	}

	public String getResultClassName() {
		return resultClassName;
	}

	public boolean isResultIsVoid() {
		return resultIsVoid;
	}

	public Collection<Object> getInjectedAndParams() {
		return injectedAndParams;
	}

	public boolean isHasParameters() {
		return hasParameters;
	}

	public boolean isHasProgressAware() {
		return hasProgressAware;
	}

	public String getProgressStateName() {
		return progressStateName;
	}

	public boolean isHasCancelHandler() {
		return hasCancelHandler;
	}

	public String getSubscriberResultClassName() {
		return subscriberResultClassName;
	}

	public static class InjectedModel {

		private final String type;
		private final String lowerCaseName;
		private final String methodName;

		public InjectedModel(MethodParameter parameter) {
			this.type = parameter.getSourceCodeRepresentationOfType();
			this.methodName = parameter.getName();
			this.lowerCaseName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
		}

		public String getType() {
			return type;
		}

		public String getLowerCaseName() {
			return lowerCaseName;
		}

		public String getMethodName() {
			return methodName;
		}

		public boolean isParameter() {
			return false;
		}

	}

	public static class ParameterModel {

		private final String type;
		private final String lowerCaseName;
		private final String upperCaseName;
		private final String methodName;
		private final boolean optional;

		public ParameterModel(MethodParameter parameter) {
			this.type = parameter.getSourceCodeRepresentationOfType();
			this.methodName = parameter.getName();
			this.lowerCaseName = Character.toLowerCase(methodName.charAt(0)) + methodName.substring(1);
			this.upperCaseName = Character.toUpperCase(lowerCaseName.charAt(0)) + lowerCaseName.substring(1);
			this.optional = parameter.hasAnnotation(Optional.class);
		}

		public boolean isOptional() {
			return optional;
		}

		public String getType() {
			return type;
		}

		public String getLowerCaseName() {
			return lowerCaseName;
		}

		public String getUpperCaseName() {
			return upperCaseName;
		}

		public String getMethodName() {
			return methodName;
		}

		public boolean isParameter() {
			return true;
		}

	}

}
