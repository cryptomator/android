package org.cryptomator.data.db.templating

import java.io.InputStream
import dagger.Subcomponent

@DbTemplateScoped
@Subcomponent(modules = [DbTemplateModule::class])
interface DbTemplateComponent {

	@DbTemplateScoped
	fun templateStream(): InputStream

	@Subcomponent.Factory
	interface Factory {

		fun create(): DbTemplateComponent

	}
}