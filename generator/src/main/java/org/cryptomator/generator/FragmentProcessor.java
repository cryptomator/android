package org.cryptomator.generator;

import org.cryptomator.generator.model.FragmentModel;
import org.cryptomator.generator.model.FragmentsModel;
import org.cryptomator.generator.templates.FragmentsTemplate;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.Fragment")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class FragmentProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		FragmentsModel.Builder fragmentsModelBuilder = FragmentsModel.builder();
		List<Element> fragmentAnnotatedElements = new ArrayList<>();
		for (Element element : environment.getElementsAnnotatedWith(Fragment.class)) {
			fragmentAnnotatedElements.add(element);
			fragmentsModelBuilder.add(new FragmentModel(utils.type((TypeElement) element)));
		}
		if (!fragmentAnnotatedElements.isEmpty()) {
			generateFragments(fragmentsModelBuilder.build(), fragmentAnnotatedElements);
		}
	}

	private void generateFragments(FragmentsModel model, List<Element> fragmentAnnotatedElements) throws IOException {
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + '.' + model.getClassName(), fragmentAnnotatedElements.stream().toArray(Element[]::new));
		Writer writer = file.openWriter();
		new FragmentsTemplate(model).render(writer);
		writer.close();
	}

}
