package org.cryptomator.presentation.util

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SharepointUrlParserTest {

	@ParameterizedTest
	@CsvSource(
		"https://contoso.sharepoint.com/sites/team, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/teams/team, https://contoso.sharepoint.com/teams/team",
		"https://contoso.sharepoint.com/sites/team/, https://contoso.sharepoint.com/sites/team",
		"'  https://contoso.sharepoint.com/sites/team  ', https://contoso.sharepoint.com/sites/team",
		"https://Contoso.SharePoint.com/Sites/team, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/sites/team/Shared%20Documents/Forms/AllItems.aspx, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/sites/team/SitePages/Home.aspx?web=1#section, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/sites/team%20a/Shared%20Documents, https://contoso.sharepoint.com/sites/team%20a",
		"https://contoso.sharepoint.com, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/SitePages/DevHome.aspx, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/_layouts/15/sharepoint.aspx/onedrive, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/sites, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/:w:/r/sites/team/_layouts/15/Doc.aspx?sourcedoc=%7B9C9A%7D&file=Document.docx, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/:w:/r/_layouts/15/Doc.aspx?sourcedoc=%7B9C9A%7D&file=Document.docx, https://contoso.sharepoint.com",
		"https://contoso.sharepoint.com/:x:/s/team/EToken, https://contoso.sharepoint.com/sites/team",
		"https://contoso.sharepoint.com/:x:/s/team%20a/EToken, https://contoso.sharepoint.com/sites/team%20a",
		"https://contoso.sharepoint.com/:f:/t/team/EToken, https://contoso.sharepoint.com/teams/team",
		"https://contoso.sharepoint.com/:li:/s/team/EToken, https://contoso.sharepoint.com/sites/team"
	)
	fun testSiteUrlOfSharepointLink(url: String, expectedSiteUrl: String) {
		assertThat(SharepointUrlParser.siteUrlOf(url), `is`(expectedSiteUrl))
	}

	@ParameterizedTest
	@ValueSource(
		strings = [
			"",
			"https://",
			"contoso.sharepoint.com/sites/team",
			"http://contoso.sharepoint.com/sites/team",
			"https://example.com/sites/team",
			"https://contoso.sharepoint.com.example.com/sites/team",
			"https://contoso-my.sharepoint.com/personal/user_contoso_com",
			"https://contoso.sharepoint.com/:w:/g/EToken",
			"https://contoso.sharepoint.com/:fl:/g/contentstorage/EToken",
			"https://contoso.sharepoint.com/:w:/",
			"https://contoso.sharepoint.com/sites/team with space"
		]
	)
	fun testSiteUrlOfNonSharepointLinkIsNull(url: String) {
		assertThat(SharepointUrlParser.siteUrlOf(url), `is`(nullValue()))
	}
}
