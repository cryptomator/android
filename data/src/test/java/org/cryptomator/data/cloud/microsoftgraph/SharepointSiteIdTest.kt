package org.cryptomator.data.cloud.microsoftgraph

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SharepointSiteIdTest {

	@ParameterizedTest
	@CsvSource(
		"https://contoso.sharepoint.com, contoso.sharepoint.com",
		"https://contoso.sharepoint.com/sites/team, contoso.sharepoint.com:/sites/team",
		"https://contoso.sharepoint.com/teams/team%20a, contoso.sharepoint.com:/teams/team%20a"
	)
	fun testSiteIdOfSiteUrl(siteUrl: String, expectedSiteId: String) {
		assertThat(SharepointSiteId.of(siteUrl), `is`(expectedSiteId))
	}
}
