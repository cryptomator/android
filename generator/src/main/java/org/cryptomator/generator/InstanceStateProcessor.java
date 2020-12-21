package org.cryptomator.generator;

import org.cryptomator.generator.model.InstanceStateModel;
import org.cryptomator.generator.model.InstanceStatesModel;
import org.cryptomator.generator.templates.InstanceStateTemplate;
import org.cryptomator.generator.utils.Field;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.InstanceState")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class InstanceStateProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		InstanceStatesModel instanceStates = new InstanceStatesModel();
		for (Element element : environment.getElementsAnnotatedWith(InstanceState.class)) {
			instanceStates.add(new Field(utils, (VariableElement) element));
		}
		for (InstanceStateModel model : (Iterable<InstanceStateModel>) (instanceStates.instanceStates()::iterator)) {
			generateInstanceStates(model);
		}
	}

	private void generateInstanceStates(InstanceStateModel model) throws IOException {
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + ".InstanceStates", model.elements());
		Writer writer = file.openWriter();
		new InstanceStateTemplate(model).render(writer);
		writer.close();
	}

}
