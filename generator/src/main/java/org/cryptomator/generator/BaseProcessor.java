package org.cryptomator.generator;

import org.cryptomator.generator.utils.Utils;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

abstract class BaseProcessor extends AbstractProcessor {

	Filer filer;
	Messager messager;
	Utils utils;

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		this.filer = processingEnv.getFiler();
		this.messager = processingEnv.getMessager();
		this.utils = new Utils(messager, processingEnv.getTypeUtils(), processingEnv.getElementUtils());
	}

	@Override
	public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			try {
				messager.printMessage(Diagnostic.Kind.NOTE, "Running " + getClass().getSimpleName());
				doProcess(annotations, roundEnv);
				messager.printMessage(Diagnostic.Kind.NOTE, getClass().getSimpleName() + " finished");
			} catch (ProcessorException e) {
				messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
			} catch (IOException e) {
				throw new RuntimeException("Processing failed", e);
			}
		} catch (RuntimeException | Error e) {
			StringBuilder sb = new StringBuilder();
			sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
			Throwable current = e;
			do {
				for (StackTraceElement stack : current.getStackTrace()) {
					sb.append('\n').append('\t').append(stack.getClassName()).append(':').append(stack.getLineNumber()).append('#').append(stack.getMethodName());
				}
				current = current.getCause();
				if (current != null) {
					sb.append("\nCaused by ").append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
				}
			} while (current != null);
			messager.printMessage(Diagnostic.Kind.NOTE, getClass().getSimpleName() + " failed: " + sb.toString());
			throw e;
		}
		return true;
	}

	private boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment environment) throws IOException {
		process(environment);
		return true;
	}

	protected abstract void process(RoundEnvironment environment) throws IOException;

}
