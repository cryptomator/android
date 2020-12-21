package org.cryptomator.generator.utils;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

public class Method {

	private final Utils utils;
	private final ExecutableElement delegate;

	public Method(Utils utils, ExecutableElement executableElement) {
		this.utils = utils;
		if (!isRegularMethod(executableElement) && !isConstructor(executableElement)) {
			throw new IllegalArgumentException(executableElement + " not a method or constructor");
		}
		this.delegate = executableElement;
	}

	public Element element() {
		return delegate;
	}

	public String name() {
		return delegate.getSimpleName().toString();
	}

	public static boolean isRegularMethod(ExecutableElement executableElement) {
		String name = executableElement.getSimpleName().toString();
		return !"".equals(name) && !name.startsWith("<");
	}

	public static boolean isConstructor(ExecutableElement executableElement) {
		String name = executableElement.getSimpleName().toString();
		return name.equals("<init>");
	}

	public boolean isStatic() {
		return delegate.getModifiers().contains(Modifier.STATIC);
	}

	public boolean isPackagePrivate() {
		return !delegate.getModifiers().contains(Modifier.PRIVATE) && !delegate.getModifiers().contains(Modifier.PUBLIC) && !delegate.getModifiers().contains(Modifier.PROTECTED);
	}

	public boolean returnsVoid() {
		return delegate.getReturnType().getKind() == TypeKind.VOID;
	}

	public Type returnType() {
		return new Type(utils, delegate.getReturnType());
	}

	public String getSourceCodeRepresentationOfType() {
		return delegate.getReturnType().toString();
	}

	public Stream<MethodParameter> parameters() {
		return delegate.getParameters().stream().map(variableElement -> new MethodParameter(utils, variableElement));
	}

	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return delegate.getAnnotation(annotationType) != null;
	}

	public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
		return delegate.getAnnotation(annotationType);
	}

	public Type declaringType() {
		return new Type(utils, (TypeElement) delegate.getEnclosingElement());
	}
}
