package org.cryptomator.generator;

import javax.lang.model.element.Element;

public class ProcessorException extends RuntimeException {

	private final Element element;

	public ProcessorException(String message, Element element) {
		super("Generator: " + message);
		this.element = element;
	}

	public Element getElement() {
		return element;
	}
}
