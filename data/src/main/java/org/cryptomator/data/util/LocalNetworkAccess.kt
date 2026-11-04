package org.cryptomator.data.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.ProxyInfo
import android.net.RouteInfo
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Since Android 17 apps can't reach servers in the local network without the ACCESS_LOCAL_NETWORK runtime permission,
 * connections just time out. Detects that case up front so it can be reported as a missing permission instead.
 *
 * Follows how Android decides it for the network interface a connection leaves through: multicast and broadcast, and
 * the subnets of the interface's own IPv6 and private, link-local or CGNAT IPv4 addresses. Networks with a proxy aren't
 * restricted. `.local` names that only mDNS can resolve are local too.
 */
class LocalNetworkAccess(
	private val context: Context,
	private val sdkInt: Int = Build.VERSION.SDK_INT,
	private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName
) : Interceptor {

	/**
	 * @throws LocalNetworkAccessDeniedException if [host] is in the local network and the app isn't allowed to access it
	 */
	@Throws(LocalNetworkAccessDeniedException::class)
	fun check(host: String) {
		if (!restrictsLocalNetwork() || permissionGranted()) {
			return
		}
		val network = ActiveNetwork.of(context) ?: return
		val addresses = resolve(host)
		// without the permission, mDNS can't answer
		if (addresses.isEmpty() && isMdnsName(host) || addresses.any { network.isLocal(it) }) {
			throw LocalNetworkAccessDeniedException(host)
		}
	}

	/**
	 * Also covers IP literals, for which OkHttp doesn't consult its `Dns`.
	 */
	override fun intercept(chain: Interceptor.Chain): Response {
		check(chain.request().url.host)
		return chain.proceed(chain.request())
	}

	@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.CINNAMON_BUN)
	private fun restrictsLocalNetwork(): Boolean {
		return sdkInt >= Build.VERSION_CODES.CINNAMON_BUN
	}

	private fun permissionGranted(): Boolean {
		return context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
	}

	private fun resolve(host: String): List<InetAddress> {
		return try {
			lookup(host).toList()
		} catch (e: UnknownHostException) {
			// the connection itself will report it
			emptyList()
		}
	}

	/**
	 * The networks connections go through, read once per check. Their addresses are `null` if Android doesn't restrict
	 * them.
	 *
	 * @param vpnRoutes `null` unless it's a VPN
	 * @param otherNetworks addresses of the networks that carry what a VPN excludes
	 */
	@RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
	private class ActiveNetwork(
		private val linkAddresses: List<LinkAddress>?,
		private val vpnRoutes: List<RouteInfo>?,
		otherNetworks: () -> List<LinkAddress>?
	) {

		private val otherNetworks by lazy(otherNetworks)

		fun isLocal(address: InetAddress): Boolean {
			val addresses = addressesOfInterfaceTo(address) ?: return false
			// the device's own addresses are reached through the loopback interface
			if (addresses.any { it.address == address }) {
				return false
			}
			return MULTICAST_AND_BROADCAST_PREFIXES.any { it.contains(address) } || isOnLink(address, addresses)
		}

		private fun addressesOfInterfaceTo(address: InetAddress): List<LinkAddress>? {
			if (vpnRoutes == null) {
				return linkAddresses
			}
			val route = vpnRoutes //
				.filter { Prefix(it.destination.address, it.destination.prefixLength).contains(address) } //
				.maxByOrNull { it.destination.prefixLength }
			// excluded from the tunnel, e.g. by a split VPN that allows LAN access
			return if (route == null || route.type == RouteInfo.RTN_THROW) otherNetworks else linkAddresses
		}

		companion object {

			/**
			 * @return `null` if nothing is restricted
			 */
			fun of(context: Context): ActiveNetwork? {
				val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return null
				val network = connectivityManager.activeNetwork ?: return ActiveNetwork(emptyList(), null) { null }
				val linkProperties = connectivityManager.getLinkProperties(network)
				// the global proxy, otherwise the one of the active network
				val defaultProxy = connectivityManager.defaultProxy
				if (isProxySet(defaultProxy) && defaultProxy != linkProperties?.httpProxy) {
					return null
				}
				val isVpn = connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
				// a VPN's proxy doesn't cover the destinations it excludes from the tunnel
				if (!isVpn && isProxySet(linkProperties?.httpProxy)) {
					return null
				}
				return ActiveNetwork(
					linkAddresses = restrictedAddressesOf(linkProperties),
					vpnRoutes = if (isVpn) linkProperties?.routes.orEmpty() else null,
					otherNetworks = { otherNetworksOf(connectivityManager) }
				)
			}

			@Suppress("DEPRECATION") // the replacement, network callbacks, can't be queried on demand
			private fun otherNetworksOf(connectivityManager: ConnectivityManager): List<LinkAddress>? {
				return connectivityManager.allNetworks
					.filter { connectivityManager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false }
					.mapNotNull { restrictedAddressesOf(connectivityManager.getLinkProperties(it)) }
					.takeIf { it.isNotEmpty() }
					?.flatten()
			}

			private fun restrictedAddressesOf(linkProperties: LinkProperties?): List<LinkAddress>? {
				return if (isProxySet(linkProperties?.httpProxy)) null else linkProperties?.linkAddresses.orEmpty()
			}

			private fun isProxySet(proxy: ProxyInfo?): Boolean {
				return proxy != null && (!proxy.host.isNullOrEmpty() || !proxy.pacFileUrl?.toString().isNullOrEmpty())
			}
		}
	}

	/**
	 * An IP prefix, as [android.net.IpPrefix] isn't usable in unit tests.
	 */
	private class Prefix(val address: InetAddress, val length: Int) {

		fun contains(other: InetAddress): Boolean {
			val bytes = address.address
			val otherBytes = other.address
			if (bytes.size != otherBytes.size) {
				return false
			}
			return (0 until length).all { bit ->
				val mask = 0x80 ushr (bit % 8)
				bytes[bit / 8].toInt() and mask == otherBytes[bit / 8].toInt() and mask
			}
		}

		companion object {

			fun of(prefix: String): Prefix {
				val (address, length) = prefix.split('/')
				return Prefix(InetAddress.getByName(address), length.toInt())
			}
		}
	}

	companion object {

		// as in Android's NetworkStackConstants
		private val MULTICAST_AND_BROADCAST_PREFIXES = listOf("224.0.0.0/4", "ff00::/8", "255.255.255.255/32").map(Prefix::of)
		private val IPV4_LOCAL_PREFIXES = listOf("169.254.0.0/16", "100.64.0.0/10", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16").map(Prefix::of)

		fun isMdnsName(host: String): Boolean {
			return host.trimEnd('.').endsWith(".local", ignoreCase = true)
		}

		/**
		 * @param linkAddresses the addresses of the network interface [address] is reached through
		 */
		fun isOnLink(address: InetAddress, linkAddresses: List<LinkAddress>): Boolean {
			return linkAddresses.any { linkAddress ->
				val subnet = Prefix(linkAddress.address, linkAddress.prefixLength)
				isPrefixLocal(subnet) && subnet.contains(address)
			}
		}

		private fun isPrefixLocal(subnet: Prefix): Boolean {
			return if (subnet.address is Inet6Address) {
				subnet.length > 0
			} else {
				IPV4_LOCAL_PREFIXES.any { it.length <= subnet.length && it.contains(subnet.address) }
			}
		}
	}
}

class LocalNetworkAccessDeniedException(host: String) : IOException("Missing permission to access $host in the local network")
