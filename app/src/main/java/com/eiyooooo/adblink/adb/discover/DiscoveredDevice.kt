package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import com.eiyooooo.adblink.model.connection.ConnectionEndpoint
import com.eiyooooo.adblink.model.connection.ConnectionHost
import com.eiyooooo.adblink.model.connection.ConnectionType
import com.eiyooooo.adblink.model.connection.normalizeEndpointList
import com.eiyooooo.adblink.model.connection.normalizeHostList
import timber.log.Timber

data class DiscoveredDevice(
    val deviceSerial: String,
    val serviceName: String,
    val hosts: List<ConnectionHost>,
    val connectionEndpoints: List<ConnectionEndpoint>,
    val serviceTypes: Set<AdbDiscoverServiceType>,
    val deviceName: String? = null
) {

    fun mergeWith(other: DiscoveredDevice): DiscoveredDevice {
        val preferredServiceName = when {
            other.connectionEndpoints.any { it.type == ConnectionType.TLS } -> other.serviceName
            connectionEndpoints.any { it.type == ConnectionType.TLS } -> serviceName
            else -> other.serviceName.ifBlank { serviceName }
        }

        return copy(
            serviceName = preferredServiceName,
            hosts = normalizeHostList(hosts, other.hosts),
            connectionEndpoints = normalizeEndpointList(connectionEndpoints, other.connectionEndpoints),
            serviceTypes = serviceTypes + other.serviceTypes
        )
    }

    companion object {
        /**
         * Extracts device serial from service name
         * Expected format: adb-{serial}-{suffix} or adb-{serial}
         */
        private fun extractSerialFromServiceName(serviceName: String): String? {
            return Regex("""adb-([^-]+)""").find(serviceName)?.groupValues?.get(1)
        }

        fun fromNsdServiceInfo(serviceInfo: NsdServiceInfo): DiscoveredDevice? {
            val deviceSerial = extractSerialFromServiceName(serviceInfo.serviceName) ?: run {
                Timber.w("Failed to extract device serial from service name: ${serviceInfo.serviceName}")
                return null
            }

            val hostAddresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
            ) {
                serviceInfo.hostAddresses.mapNotNull { it.hostAddress }
            } else {
                @Suppress("DEPRECATION")
                listOfNotNull(serviceInfo.host?.hostAddress)
            }

            if (hostAddresses.isEmpty()) {
                Timber.w("No valid host addresses found for device: $deviceSerial")
                return null
            }

            val serviceType = AdbDiscoverServiceType.fromNsdServiceInfo(serviceInfo) ?: run {
                Timber.w("Unknown service type: ${serviceInfo.serviceType}")
                return null
            }

            val deviceName = serviceInfo.attributes?.let { attrs ->
                attrs["name"]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
            }

            val connectionType = when (serviceType) {
                AdbDiscoverServiceType.ADB_TCP -> ConnectionType.TCP
                AdbDiscoverServiceType.ADB_TLS_CONNECT,
                AdbDiscoverServiceType.ADB_TLS_PAIRING -> ConnectionType.TLS
            }

            val hosts = hostAddresses.mapNotNull { host ->
                val trimmed = host.trim()
                trimmed.takeIf { it.isNotEmpty() }?.let {
                    ConnectionHost(host = trimmed)
                }
            }.distinctBy { it.host.lowercase() }

            val endpoints = listOf(
                ConnectionEndpoint(
                    port = serviceInfo.port,
                    type = connectionType
                )
            )

            return DiscoveredDevice(
                deviceSerial = deviceSerial,
                serviceName = serviceInfo.serviceName,
                hosts = hosts,
                connectionEndpoints = endpoints,
                serviceTypes = setOf(serviceType),
                deviceName = deviceName
            )
        }
    }
}
