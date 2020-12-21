package org.cryptomator.generator.utils;

import java.lang.annotation.Annotation;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

public class Field {

	private final Utils utils;
	private final VariableElement delegate;

	public Field(Utils utils, VariableElement variableElement) {
		this.utils = utils;
		this.delegate = variableElement;
	}

	public Element element() {
		return delegate;
	}

	public String name() {
		return delegate.getSimpleName().toString();
	}

	public boolean isStatic() {
		return delegate.getModifiers().contains(Modifier.STATIC);
	}

	public boolean isPackagePrivate() {
		return !delegate.getModifiers().contains(Modifier.PRIVATE) && !delegate.getModifiers().contains(Modifier.PUBLIC) && !delegate.getModifiers().contains(Modifier.PROTECTED);
	}

	public Type declaringType() {
		return new Type(utils, (TypeElement) delegate.getEnclosingElement());
	}

	public Type type() {
		return new Type(utils, delegate.asType());
	}

	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return delegate.getAnnotation(annotationType) != null;
	}

	public boolean isPrivate() {
		return delegate.getModifiers().contains(Modifier.PRIVATE);
	}
}
