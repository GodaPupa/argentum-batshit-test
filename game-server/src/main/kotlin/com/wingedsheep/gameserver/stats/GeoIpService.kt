package com.wingedsheep.gameserver.stats

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** A coarse location for an IP. [country] is null when it could not be resolved. */
@Serializable
data class GeoLocation(
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
)

/**
 * Resolves IPs → coarse locations for the admin dashboard's "where are players connecting from"
 * estimate. Uses the free ip-api.com batch endpoint and caches every resolved IP in-process, so
 * repeated dashboard loads hit the network only for IPs seen since the last server start. Raw IPs
 * never leave this service toward clients — only the aggregated locations do.
 *
 * Only wired when accounts are enabled (the dashboard that uses it is gated the same way).
 */
@Service
@ConditionalOnProperty(name = ["accounts.enabled"], havingValue = "true")
class GeoIpService {
    private val logger = LoggerFactory.getLogger(GeoIpService::class.java)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val cache = ConcurrentHashMap<String, GeoLocation>()

    /** Resolve [ips] to locations, using the cache and batching un-cached public IPs. */
    fun resolve(ips: List<String>): Map<String, GeoLocation> {
        val result = HashMap<String, GeoLocation>(ips.size)
        val toFetch = ArrayList<String>()
        for (ip in ips.distinct()) {
            val cached = cache[ip]
            when {
                cached != null -> result[ip] = cached
                isPrivate(ip) -> LOCAL.also { cache[ip] = it; result[ip] = it }
                else -> toFetch += ip
            }
        }
        // ip-api.com batch allows up to 100 IPs per request.
        for (batch in toFetch.chunked(100)) {
            runCatching { fetchBatch(batch) }
                .onFailure { logger.warn("GeoIP batch lookup failed: {}", it.message) }
                .getOrNull()
                ?.forEach { (ip, loc) -> cache[ip] = loc; result[ip] = loc }
        }
        // Anything still unresolved (network failure) → empty location, but don't poison the cache.
        for (ip in ips) result.putIfAbsent(ip, UNKNOWN)
        return result
    }

    private fun fetchBatch(ips: List<String>): Map<String, GeoLocation> {
        val body = json.encodeToString(ips)
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://ip-api.com/batch?fields=status,country,countryCode,regionName,city,query"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) {
            logger.warn("GeoIP API error (status {})", response.statusCode())
            return emptyMap()
        }
        return json.decodeFromString<List<IpApiResult>>(response.body())
            .filter { it.query != null }
            .associate { r ->
                r.query!! to if (r.status == "success") {
                    GeoLocation(r.country, r.countryCode, r.regionName, r.city)
                } else UNKNOWN
            }
    }

    private fun isPrivate(ip: String): Boolean = runCatching {
        val addr = InetAddress.getByName(ip)
        addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress
    }.getOrDefault(true)

    @Serializable
    private data class IpApiResult(
        val status: String? = null,
        val country: String? = null,
        val countryCode: String? = null,
        val regionName: String? = null,
        val city: String? = null,
        val query: String? = null,
    )

    private companion object {
        val UNKNOWN = GeoLocation()
        val LOCAL = GeoLocation(country = "Local network", countryCode = "LOCAL")
    }
}
