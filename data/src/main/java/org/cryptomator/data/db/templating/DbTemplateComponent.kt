package org.cryptomator.data.db.templating

import java.io.File
import dagger.Subcomponent

@DbTemplateScoped
@Subcomponent(modules = [DbTemplateModule::class])
interface DbTemplateComponent {

	@DbTemplateScoped
	fun templateFile(): File

	@Subcomponent.Factory
	interface Factory {

		fun create(): DbTemplateComponent

	}
}