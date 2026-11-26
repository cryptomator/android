package org.cryptomator.domain

private const val CHOICE = "{choice}"
private const val ACCOUNT_ID = "{account-id}"
private const val SERVER = "{server}"

enum class S3Provider(private val endpointTemplate: String, val choices: List<String> = emptyList(), private val fixedRegion: String? = null) {

	AKAMAI("https://$CHOICE.linodeobjects.com", choices = listOf("us-east-1", "us-southeast-1", "us-mia-1", "us-iad-1", "us-iad-10", "us-iad-18", "us-ord-1", "us-ord-10", "us-lax-1", "us-lax-4", "us-sea-1", "us-sea-9", "br-gru-1", "eu-central-1", "de-fra-1", "fr-par-1", "fr-par-13", "gb-lon-1", "nl-ams-1", "es-mad-1", "it-mil-1", "se-sto-1", "ap-south-1", "sg-sin-1", "in-maa-1", "in-maa-5", "in-bom-1", "id-cgk-1", "jp-osa-1", "jp-osa-3", "jp-tyo-1", "au-mel-1"), fixedRegion = "us-east-1"), //
	AMAZON_S3("https://s3.$CHOICE.amazonaws.com", choices = listOf("us-east-1", "us-east-2", "us-west-1", "us-west-2", "ca-central-1", "ca-west-1", "mx-central-1", "sa-east-1", "eu-central-1", "eu-central-2", "eu-west-1", "eu-west-2", "eu-west-3", "eu-north-1", "eu-south-1", "eu-south-2", "il-central-1", "me-central-1", "me-south-1", "af-south-1", "ap-south-1", "ap-south-2", "ap-east-1", "ap-east-2", "ap-northeast-1", "ap-northeast-2", "ap-northeast-3", "ap-southeast-1", "ap-southeast-2", "ap-southeast-3", "ap-southeast-4", "ap-southeast-5", "ap-southeast-6", "ap-southeast-7")), //
	BACKBLAZE_B2("https://s3.$CHOICE.backblazeb2.com", choices = listOf("us-west-000", "us-west-001", "us-west-002", "us-west-004", "us-east-005", "eu-central-003", "ca-east-006")), //
	CLOUDFLARE_R2("https://$ACCOUNT_ID.r2.cloudflarestorage.com", fixedRegion = "auto"), //
	DIGITALOCEAN_SPACES("https://$CHOICE.digitaloceanspaces.com", choices = listOf("nyc3", "sfo2", "sfo3", "atl1", "tor1", "ams3", "fra1", "lon1", "blr1", "sgp1", "syd1")), //
	EXOSCALE("https://sos-$CHOICE.exo.io", choices = listOf("at-vie-1", "at-vie-2", "bg-sof-1", "ch-dk-2", "ch-gva-2", "de-fra-1", "de-muc-1", "hr-zag-1")), //
	GOOGLE_CLOUD_STORAGE("https://storage.googleapis.com", fixedRegion = "auto"), //
	HETZNER("https://$CHOICE.your-objectstorage.com", choices = listOf("fsn1", "nbg1", "hel1")), //
	IONOS("https://s3.$CHOICE.ionoscloud.com", choices = listOf("eu-central-1", "eu-central-2", "eu-central-3", "eu-central-4", "eu-south-2", "us-central-1")) {
		// IONOS serves eu-central-1 under the region name "de"
		override fun region(choice: String): String {
			return if (choice == "eu-central-1") "de" else choice
		}
	}, //
	MINIO(SERVER, choices = listOf("us-east-1")), //
	OVHCLOUD("https://s3.$CHOICE.io.cloud.ovh.net", choices = listOf("gra", "rbx", "sbg", "eu-west-par", "eu-south-mil", "de", "uk", "waw", "bhs", "ca-east-tor", "sgp", "ap-southeast-syd", "ap-south-mum")), //
	SCALEWAY("https://s3.$CHOICE.scw.cloud", choices = listOf("fr-par", "nl-ams", "pl-waw", "it-mil")), //
	STORJ("https://gateway.storjshare.io", fixedRegion = "us-east-1"), //
	VULTR("https://$CHOICE.vultrobjects.com", choices = listOf("ewr1", "ewr2", "atl1", "atl2", "chi3", "lax1", "sea1", "sjc1", "ams1", "ams2", "lhr1", "mxp1", "blr1", "blr2", "del1", "nrt1", "sgp1", "sgp2", "syd1"), fixedRegion = "us-east-1"), //
	WASABI("https://s3.$CHOICE.wasabisys.com", choices = listOf("us-east-1", "us-east-2", "us-central-1", "us-west-1", "us-west-2", "ca-central-1", "eu-central-1", "eu-central-2", "eu-west-1", "eu-west-2", "eu-west-3", "eu-south-1", "ap-northeast-1", "ap-northeast-2", "ap-southeast-1", "ap-southeast-2"));

	val requiresServer: Boolean
		get() = endpointTemplate.contains(SERVER)

	val requiresAccountId: Boolean
		get() = endpointTemplate.contains(ACCOUNT_ID)

	val choiceIsEndpointName: Boolean
		get() = choices.isNotEmpty() && fixedRegion != null

	fun endpoint(serverUrl: String, accountId: String, choice: String): String {
		return endpointTemplate //
			.replace(SERVER, serverUrl.trimEnd('/')) //
			.replace(ACCOUNT_ID, accountId) //
			.replace(CHOICE, choice)
	}

	open fun region(choice: String): String {
		return fixedRegion ?: choice
	}
}
