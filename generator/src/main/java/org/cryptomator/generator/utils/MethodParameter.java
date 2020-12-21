package org.cryptomator.generator.utils;

import org.cryptomator.generator.ProcessorException;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

public class MethodParameter {

	private final Utils utils;
	private final VariableElement delegate;

	public MethodParameter(Utils utils, VariableElement variableElement) {
		this.utils = utils;
		this.delegate = variableElement;
	}

	public String getName() {
		return delegate.getSimpleName().toString();
	}

	public Type getTypeArgument(int index) {
		List<? extends TypeMirror> typeArguments = ((DeclaredType) delegate.asType()).getTypeArguments();
		TypeMirror mirror;
		try {
			mirror = typeArguments.get(index);
		} catch (IndexOutOfBoundsException e) {
			throw new ProcessorException("Required declaringType parameter is missing", delegate);
		}
		return new Type(utils, (TypeElement) ((DeclaredType) mirror).asElement());
	}

	public Type getType() {
		return new Type(utils, (TypeElement) ((DeclaredType) delegate.asType()).asElement());
	}

	public String getSourceCodeRepresentationOfType() {
		return delegate.asType().toString();
	}

	public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
		return delegate.getAnnotation(annotationType) != null;
	}

	public Element element() {
		return delegate;
	}
}
