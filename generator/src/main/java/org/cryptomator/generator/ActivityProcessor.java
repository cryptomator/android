package org.cryptomator.generator;

import org.cryptomator.generator.model.ActivitiesModel;
import org.cryptomator.generator.model.ActivityModel;
import org.cryptomator.generator.templates.ActivitiesTemplate;

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

@SupportedAnnotationTypes("org.cryptomator.generator.Activity")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ActivityProcessor extends BaseProcessor {

	@Override
	public void process(RoundEnvironment environment) throws IOException {
		ActivitiesModel.Builder activitiesModelBuilder = ActivitiesModel.builder();
		List<Element> activityAnnotatedElements = new ArrayList<>();
		for (Element element : environment.getElementsAnnotatedWith(Activity.class)) {
			activityAnnotatedElements.add(element);
			activitiesModelBuilder.add(new ActivityModel(utils.type((TypeElement) element)));
		}
		if (!activityAnnotatedElements.isEmpty()) {
			generateActivities(activitiesModelBuilder.build(), activityAnnotatedElements);
		}
	}

	private void generateActivities(ActivitiesModel model, List<Element> activityAnnotatedElements) throws IOException {
		JavaFileObject file = filer.createSourceFile(model.getJavaPackage() + '.' + model.getClassName(), activityAnnotatedElements.stream().toArray(Element[]::new));
		Writer writer = file.openWriter();
		new ActivitiesTemplate(model).render(writer);
		writer.close();
	}

}
