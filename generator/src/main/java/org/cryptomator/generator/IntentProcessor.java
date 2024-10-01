package org.cryptomator.generator;

import org.cryptomator.generator.model.IntentBuilderModel;
import org.cryptomator.generator.model.IntentReaderModel;
import org.cryptomator.generator.model.IntentsModel;
import org.cryptomator.generator.templates.IntentBuilderTemplate;
import org.cryptomator.generator.templates.IntentReaderTemplate;
import org.cryptomator.generator.templates.IntentsTemplate;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.Intent")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class IntentProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		IntentsModel.Builder intentsModelBuilder = IntentsModel.builder();
		List<Element> intentAnnotatedElements = new ArrayList<>();
		for (Element element : environment.getElementsAnnotatedWith(Intent.class)) {
			intentAnnotatedElements.add(element);
			intentsModelBuilder.add(generateIntentBuilder((TypeElement) element));
			intentsModelBuilder.add(generateIntentReader((TypeElement) element));
		}
		if (!intentAnnotatedElements.isEmpty()) {
			intentAnnotatedElements.sort(Comparator.comparing(e -> e.getSimpleName().toString()));
			generateIntents(intentsModelBuilder.build(), intentAnnotatedElements);
		}
	}

	private IntentBuilderModel generateIntentBuilder(TypeElement element) throws IOException {
		IntentBuilderModel model = new IntentBuilderModel(element);
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + '.' + model.getClassName(), element);
		Writer writer = file.openWriter();
		new IntentBuilderTemplate(model).render(writer);
		writer.close();
		return model;
	}

	private IntentReaderModel generateIntentReader(TypeElement element) throws IOException {
		IntentReaderModel model = new IntentReaderModel(element);
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + '.' + model.getClassName(), element);
		Writer writer = file.openWriter();
		new IntentReaderTemplate(model).render(writer);
		writer.close();
		return model;
	}

	private void generateIntents(IntentsModel model, List<Element> intentAnnotatedElements) throws IOException {
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + '.' + model.getClassName(), intentAnnotatedElements.stream().toArray(Element[]::new));
		Writer writer = file.openWriter();
		new IntentsTemplate(model).render(writer);
		writer.close();
	}

}
