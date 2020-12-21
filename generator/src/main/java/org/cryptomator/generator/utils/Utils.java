package org.cryptomator.generator.utils;

import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class Utils {

	final Messager messager;
	final Types types;
	final Elements elements;

	public Utils(Messager messager, Types types, Elements elements) {
		this.messager = messager;
		this.types = types;
		this.elements = elements;
	}

	public Type type(TypeMirror mirror) {
		return new Type(this, mirror);
	}

	public Type type(TypeElement element) {
		return new Type(this, element);
	}

}
