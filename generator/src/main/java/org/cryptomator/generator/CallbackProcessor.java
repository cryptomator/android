package org.cryptomator.generator;

import org.cryptomator.generator.model.CallbackModel;
import org.cryptomator.generator.model.CallbacksModel;
import org.cryptomator.generator.model.CallbacksModel.CallbacksClassModel;
import org.cryptomator.generator.templates.CallbacksTemplate;
import org.cryptomator.generator.utils.Method;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.Callback")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class CallbackProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		CallbacksModel callbacksModel = new CallbacksModel();
		for (Element element : environment.getElementsAnnotatedWith(Callback.class)) {
			try {
				CallbackModel callbackModel = new CallbackModel(new Method(utils, (ExecutableElement) element));
				callbacksModel.add(callbackModel);
			} catch (ProcessorException e) {
				messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), e.getElement());
			}
		}
		for (CallbacksClassModel packageWithCallbacks : callbacksModel.getCallbacksClasses()) {
			generateCallbacks(packageWithCallbacks);
		}
	}

	private void generateCallbacks(CallbacksClassModel callbacksClassModel) throws IOException {
		JavaFileObject file = filer.createSourceFile(callbacksClassModel.getJavaPackage() + '.' + callbacksClassModel.getCallbacksClassName());
		Writer writer = file.openWriter();
		new CallbacksTemplate(callbacksClassModel).render(writer);
		writer.close();
	}

}
