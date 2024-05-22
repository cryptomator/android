package org.cryptomator.generator;

import org.cryptomator.generator.model.UseCaseModel;
import org.cryptomator.generator.templates.UseCaseTemplate;

import java.io.IOException;
import java.io.Writer;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("org.cryptomator.generator.UseCase")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class UseCaseProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		for (Element element : environment.getElementsAnnotatedWith(UseCase.class)) {
			generateUseCase((TypeElement) element);
		}
	}

	private void generateUseCase(TypeElement element) throws IOException {
		UseCaseModel model = new UseCaseModel(utils.type(element));
		JavaFileObject file = filer.createSourceFile(model.getClassName(), element);
		Writer writer = file.openWriter();
		new UseCaseTemplate(model).render(writer);
		writer.close();
	}

}
