package org.cryptomator.generator;

import org.cryptomator.generator.model.InstanceStateModel;
import org.cryptomator.generator.model.InstanceStatesModel;
import org.cryptomator.generator.templates.InstanceStateTemplate;
import org.cryptomator.generator.utils.Field;

import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.InstanceState")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class InstanceStateProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		InstanceStatesModel instanceStates = new InstanceStatesModel();
		List<VariableElement> elements = (List<VariableElement>) environment.getElementsAnnotatedWith(InstanceState.class).stream().collect(Collectors.toList());
		elements.sort(Comparator.comparing(e -> e.getSimpleName().toString()));
		for (VariableElement element : elements) {
			instanceStates.add(new Field(utils, element));
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
