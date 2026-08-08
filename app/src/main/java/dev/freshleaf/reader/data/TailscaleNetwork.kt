package dev.freshleaf.reader.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.InetAddress

const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

/** A DNS name served by Tailscale MagicDNS. */
fun isTailscaleHostname(hostname: String): Boolean =
    hostname.trimEnd('.').lowercase().endsWith(".ts.net")

fun isTailscaleEndpoint(endpoint: String): Boolean =
    endpoint.trim().toHttpUrlOrNull()?.host?.let(::isTailscaleHostname) == true

internal enum class EndpointNetworkMode { SYSTEM, TAILSCALE_VPN }

internal fun endpointNetworkMode(hostname: String): EndpointNetworkMode =
    if (isTailscaleHostname(hostname)) EndpointNetworkMode.TAILSCALE_VPN else EndpointNetworkMode.SYSTEM

/** The user can fix this before submitting credentials again. */
class TailscaleConnectionException : FreshRssException(
    "Tailscale cannot reach this FreshRSS server. Connect Tailscale and make sure FreshLeaf is not excluded by app-based split tunneling.",
)

internal data class VpnNetworkCandidate<T>(val network: T, val isActiveVpn: Boolean)

/**
 * Return the first active VPN which can resolve [hostname]. Resolution and
 * connection are subsequently bound to this same network.
 */
internal fun <T, Address> selectResolvingVpn(
    hostname: String,
    candidates: List<VpnNetworkCandidate<T>>,
    resolve: (T, String) -> List<Address>,
): T? = candidates.asSequence()
    .filter { it.isActiveVpn }
    .mapNotNull { candidate -> candidate.network.takeIf { resolve(it, hostname).isNotEmpty() } }
    .firstOrNull()

interface EndpointHttpClientFactory {
    fun clientFor(endpoint: HttpUrl): OkHttpClient
}

internal class FixedEndpointHttpClientFactory(private val client: OkHttpClient) : EndpointHttpClientFactory {
    override fun clientFor(endpoint: HttpUrl): OkHttpClient = client
}

internal class AndroidEndpointHttpClientFactory(context: Context) : EndpointHttpClientFactory {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val systemClient = OkHttpClient()

    override fun clientFor(endpoint: HttpUrl): OkHttpClient {
        if (endpointNetworkMode(endpoint.host) == EndpointNetworkMode.SYSTEM) return systemClient

        val candidates = runCatching { connectivity.allNetworks.toList() }
            .getOrDefault(emptyList())
            .map { network ->
                VpnNetworkCandidate(
                    network = network,
                    isActiveVpn = connectivity.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                )
            }
        val network = selectResolvingVpn(endpoint.host, candidates) { candidate, hostname ->
            runCatching { candidate.getAllByName(hostname).toList() }.getOrDefault(emptyList())
        } ?: throw TailscaleConnectionException()

        val addresses = runCatching { network.getAllByName(endpoint.host).toList() }
            .getOrDefault(emptyList())
        if (addresses.isEmpty()) throw TailscaleConnectionException()

        return OkHttpClient.Builder()
            .dns(FixedDns(addresses))
            .socketFactory(network.socketFactory)
            .build()
    }
}

private class FixedDns(private val addresses: List<InetAddress>) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = addresses
}
