package org.cryptomator.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.ProxyInfo
import android.net.RouteInfo
import android.net.Uri
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.Request

class LocalNetworkAccessTest {

	private val context = mock<Context>()
	private val connectivityManager = mock<ConnectivityManager>()
	private val wifi = mock<Network>()
	private val wifiCapabilities = mock<NetworkCapabilities>()
	private val wifiLinkProperties = mock<LinkProperties>()
	private val wifiAddresses = listOf(linkAddress("192.168.178.5", 24), linkAddress("fe80::5", 64), linkAddress("2001:db8:1::5", 64))
	private val vpn = mock<Network>()
	private val vpnCapabilities = mock<NetworkCapabilities>()
	private val vpnLinkProperties = mock<LinkProperties>()
	private val vpnAddresses = listOf(linkAddress("10.8.0.2", 24))

	@BeforeEach
	fun setup() {
		whenever(wifiLinkProperties.linkAddresses).thenReturn(wifiAddresses)
		whenever(wifiCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(true)
		whenever(vpnCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)).thenReturn(true)
		whenever(vpnLinkProperties.linkAddresses).thenReturn(vpnAddresses)
		whenever(context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)).thenReturn(PackageManager.PERMISSION_DENIED)
		whenever(context.getSystemService(ConnectivityManager::class.java)).thenReturn(connectivityManager)
		whenever(connectivityManager.activeNetwork).thenReturn(wifi)
		whenever(connectivityManager.allNetworks).thenReturn(arrayOf(wifi))
		whenever(connectivityManager.getNetworkCapabilities(wifi)).thenReturn(wifiCapabilities)
		whenever(connectivityManager.getLinkProperties(wifi)).thenReturn(wifiLinkProperties)
		whenever(connectivityManager.getNetworkCapabilities(vpn)).thenReturn(vpnCapabilities)
		whenever(connectivityManager.getLinkProperties(vpn)).thenReturn(vpnLinkProperties)
	}

	@ParameterizedTest
	@CsvSource(
		"192.168.178.20, true",
		"fe80::1, true",
		"2001:db8:1::20, true",
		"192.168.1.1, false",
		"10.1.2.3, false",
		"2001:db8:2::1, false",
		"fd00::1, false",
		"8.8.8.8, false",
		"127.0.0.1, false"
	)
	fun testOnlyTheSubnetsOfTheInterfaceAreLocal(address: String, local: Boolean) {
		assertThat(LocalNetworkAccess.isOnLink(InetAddress.getByName(address), wifiAddresses), `is`(local))
	}

	@ParameterizedTest
	@CsvSource(
		"10.0.2.16, 24, 10.0.2.2, true",
		"10.0.2.16, 24, 10.0.3.1, false",
		"172.20.1.5, 16, 172.20.200.1, true",
		"172.20.1.5, 16, 172.21.0.1, false",
		"100.100.1.5, 24, 100.100.1.9, true",
		"169.254.3.4, 16, 169.254.1.1, true",
		// public subnets and ones wider than a private range don't count
		"203.0.113.5, 24, 203.0.113.9, false",
		"172.16.0.5, 8, 172.16.0.1, false",
		"2001:db8:1::5, 0, 2001:db8:2::1, false"
	)
	fun testSubnetsInPrivateRangesAreLocal(interfaceAddress: String, prefixLength: Int, address: String, local: Boolean) {
		val addresses = listOf(linkAddress(interfaceAddress, prefixLength))

		assertThat(LocalNetworkAccess.isOnLink(InetAddress.getByName(address), addresses), `is`(local))
	}

	@ParameterizedTest
	@CsvSource("nas.local, true", "NAS.LOCAL., true", "nas.example.com, false", "local, false")
	fun testMdnsNames(host: String, mdns: Boolean) {
		assertThat(LocalNetworkAccess.isMdnsName(host), `is`(mdns))
	}

	@ParameterizedTest
	@ValueSource(strings = ["nas.local", "nas-v6.example.com", "192.168.178.20", "2001:db8:1::20", "224.0.0.251", "ff02::fb", "255.255.255.255"])
	fun testLocalHostRequiresPermission(host: String) {
		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check(host) }
	}

	@ParameterizedTest
	@ValueSource(strings = ["8.8.8.8", "10.1.2.3", "192.168.1.1", "fileserver.corp.local", "unknown.example.com"])
	fun testServerBehindTheRouterDoesntRequirePermission(host: String) {
		assertDoesNotThrow { access().check(host) }
	}

	@Test
	fun testSubnetOfTheCellularNetworkIsLocal() {
		val cellularLinkProperties = mock<LinkProperties>()
		val cellularAddresses = listOf(linkAddress("10.120.4.6", 30))
		whenever(cellularLinkProperties.linkAddresses).thenReturn(cellularAddresses)
		whenever(wifiCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).thenReturn(false)
		whenever(wifiCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true)
		whenever(connectivityManager.getLinkProperties(wifi)).thenReturn(cellularLinkProperties)

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check("10.120.4.5") }
		assertDoesNotThrow { access().check("192.168.178.20") }
	}

	@Test
	fun testOwnAddressIsNoLocalNetwork() {
		assertDoesNotThrow { access().check("192.168.178.5") }
	}

	@Test
	fun testNothingIsRestrictedWhileAGlobalProxyIsSet() {
		val proxy = proxy("10.0.2.2")
		whenever(connectivityManager.defaultProxy).thenReturn(proxy)

		assertDoesNotThrow { access().check("192.168.178.20") }
	}

	@Test
	fun testNetworkWithProxyIsNotRestricted() {
		val proxy = proxy("192.168.178.1")
		whenever(wifiLinkProperties.httpProxy).thenReturn(proxy)
		whenever(connectivityManager.defaultProxy).thenReturn(proxy)

		assertDoesNotThrow { access().check("192.168.178.20") }
		// the proxy might still resolve it
		assertDoesNotThrow { access().check("nas.local") }
	}

	@Test
	fun testProxyAutoConfigIsAProxy() {
		val proxy = proxy("")
		val pacFileUrl = mock<Uri>()
		whenever(pacFileUrl.toString()).thenReturn("http://wpad/wpad.dat")
		whenever(proxy.pacFileUrl).thenReturn(pacFileUrl)
		whenever(connectivityManager.defaultProxy).thenReturn(proxy)

		assertDoesNotThrow { access().check("192.168.178.20") }
	}

	@Test
	fun testEmptyProxyIsNoProxy() {
		val proxy = proxy("")
		whenever(connectivityManager.defaultProxy).thenReturn(proxy)

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check("192.168.178.20") }
	}

	@Test
	fun testProxyOfTheVpnDoesntCoverTheExcludedLocalNetwork() {
		connectThroughVpn(route("0.0.0.0", 0, RouteInfo.RTN_UNICAST), route("192.168.0.0", 16, RouteInfo.RTN_THROW))
		val proxy = proxy("10.8.0.1")
		whenever(vpnLinkProperties.httpProxy).thenReturn(proxy)
		whenever(connectivityManager.defaultProxy).thenReturn(proxy)

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check("192.168.178.20") }
		assertDoesNotThrow { access().check("10.8.0.1") }
	}

	@Test
	fun testLocalNetworkExcludedFromVpnIsNotRestrictedIfItHasAProxy() {
		connectThroughVpn(route("0.0.0.0", 0, RouteInfo.RTN_UNICAST), route("192.168.0.0", 16, RouteInfo.RTN_THROW))
		val proxy = proxy("192.168.178.1")
		whenever(wifiLinkProperties.httpProxy).thenReturn(proxy)

		assertDoesNotThrow { access().check("192.168.178.20") }
	}

	@Test
	fun testTunneledAddressIsOnlyLocalInTheSubnetOfTheVpn() {
		connectThroughVpn(route("0.0.0.0", 0, RouteInfo.RTN_UNICAST))

		assertDoesNotThrow { access().check("192.168.178.20") }
		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check("10.8.0.1") }
	}

	@Test
	fun testLocalNetworkExcludedFromVpnRequiresPermission() {
		connectThroughVpn(route("0.0.0.0", 0, RouteInfo.RTN_UNICAST), route("192.168.0.0", 16, RouteInfo.RTN_THROW))

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check("192.168.178.20") }
	}

	@ParameterizedTest
	@ValueSource(strings = ["192.168.178.20", "2001:db8:1::20"])
	fun testLocalAddressOutsideSplitTunnelRequiresPermission(host: String) {
		connectThroughVpn(route("10.8.0.0", 24, RouteInfo.RTN_UNICAST))

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().check(host) }
	}

	@Test
	fun testAddressExcludedFromVpnIsOnlyCheckedAgainstOtherNetworks() {
		// the VPN's own subnet, but not routed through it
		connectThroughVpn(route("8.8.0.0", 16, RouteInfo.RTN_UNICAST))

		assertDoesNotThrow { access().check("10.8.0.1") }
		assertDoesNotThrow { access().check("8.8.8.8") }
	}

	@Test
	fun testGrantedPermissionAllowsLocalHost() {
		whenever(context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)).thenReturn(PackageManager.PERMISSION_GRANTED)

		assertDoesNotThrow { access().check("nas.local") }
	}

	@Test
	fun testOlderAndroidVersionsDontRestrictLocalNetwork() {
		assertDoesNotThrow { access(36).check("nas.local") }
	}

	@Test
	fun testInterceptorStopsRequestsToLocalIpLiterals() {
		val chain = chain("http://192.168.178.20:5006/webdav")

		assertThrows(LocalNetworkAccessDeniedException::class.java) { access().intercept(chain) }
		verify(chain, never()).proceed(any())
	}

	@Test
	fun testInterceptorLetsRequestsToPublicHostsThrough() {
		val chain = chain("https://8.8.8.8/webdav")

		access().intercept(chain)

		verify(chain).proceed(any())
	}

	private fun access(sdkInt: Int = 37): LocalNetworkAccess {
		return LocalNetworkAccess(context, sdkInt) { host ->
			when (host) {
				// an Active Directory domain, resolved by unicast DNS
				"fileserver.corp.local" -> arrayOf(InetAddress.getByName("10.20.0.5"))
				"nas-v6.example.com" -> arrayOf(InetAddress.getByName("2001:db8:1::30"))
				// unicast DNS can't resolve them
				"nas.local", "unknown.example.com" -> throw UnknownHostException(host)
				else -> InetAddress.getAllByName(host)
			}
		}
	}

	private fun proxy(host: String): ProxyInfo {
		val proxy = mock<ProxyInfo>()
		whenever(proxy.host).thenReturn(host)
		return proxy
	}

	private fun connectThroughVpn(vararg routes: RouteInfo) {
		whenever(vpnLinkProperties.routes).thenReturn(routes.toList())
		whenever(connectivityManager.activeNetwork).thenReturn(vpn)
		whenever(connectivityManager.allNetworks).thenReturn(arrayOf(vpn, wifi))
	}

	private fun linkAddress(address: String, prefixLength: Int): LinkAddress {
		val linkAddress = mock<LinkAddress>()
		whenever(linkAddress.address).thenReturn(InetAddress.getByName(address))
		whenever(linkAddress.prefixLength).thenReturn(prefixLength)
		return linkAddress
	}

	private fun route(destination: String, prefixLength: Int, type: Int): RouteInfo {
		val prefix = mock<IpPrefix>()
		whenever(prefix.address).thenReturn(InetAddress.getByName(destination))
		whenever(prefix.prefixLength).thenReturn(prefixLength)
		val route = mock<RouteInfo>()
		whenever(route.destination).thenReturn(prefix)
		whenever(route.type).thenReturn(type)
		return route
	}

	private fun chain(url: String): Interceptor.Chain {
		val chain = mock<Interceptor.Chain>()
		val request = Request.Builder().url(url).build()
		val response = mock<okhttp3.Response>()
		whenever(chain.request()).thenReturn(request)
		whenever(chain.proceed(any())).thenReturn(response)
		return chain
	}
}
