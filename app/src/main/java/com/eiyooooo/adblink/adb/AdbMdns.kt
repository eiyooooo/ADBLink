package com.eiyooooo.adblink.adb

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.annotation.StringDef
import com.eiyooooo.adblink.entity.SystemServices.nsdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class AdbMdns(
    @ServiceType serviceType: String,
    private val listener: (List<NsdServiceInfo>) -> Unit
) {
    private val serviceTypeFormatted: String = String.format("_%s._tcp", serviceType)
    private val discoveryListener: NsdManager.DiscoveryListener = DiscoveryListener(this)
    private val serviceInfoList: MutableList<NsdServiceInfo> = mutableListOf()
    private val serviceInfoCallbacks: MutableMap<String, ServiceInfoCallback> by lazy { mutableMapOf() }

    private val _state = MutableStateFlow(DiscoveryState.STOPPED)
    val state: StateFlow<DiscoveryState> = _state

    fun start() {
        if (_state.value != DiscoveryState.STOPPED) {
            Timber.w("ServiceType: $serviceTypeFormatted discovery already started")
            return
        }

        Timber.d("ServiceType: $serviceTypeFormatted discovery starting")
        _state.value = DiscoveryState.STARTING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
        ) {
            serviceInfoCallbacks.clear()
        }
        serviceInfoList.clear()

        try {
            nsdManager.discoverServices(serviceTypeFormatted, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Timber.w(e, "Failed to start service discovery for $serviceTypeFormatted")
            _state.value = DiscoveryState.STOPPED
        }
    }

    fun stop() {
        if (_state.value == DiscoveryState.STOPPED) {
            Timber.w("ServiceType: $serviceTypeFormatted discovery already stopped")
            return
        }

        Timber.d("ServiceType: $serviceTypeFormatted discovery stopping")
        val previousState = _state.value
        _state.value = DiscoveryState.STOPPING

        if (previousState == DiscoveryState.STARTED || previousState == DiscoveryState.STARTING) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Timber.w(e, "Failed to stop service discovery for $serviceTypeFormatted")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
        ) {
            serviceInfoCallbacks.values.forEach { callback ->
                try {
                    nsdManager.unregisterServiceInfoCallback(callback)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to unregister service info callback for ${callback.serviceInfo.serviceName}")
                }
            }
            serviceInfoCallbacks.clear()
        }
        serviceInfoList.clear()
        notifyListener()

        Timber.d("ServiceType: $serviceTypeFormatted discovery stopped in stop()")
        _state.value = DiscoveryState.STOPPED
    }

    @Suppress("DEPRECATION")
    private fun onServiceFound(serviceInfo: NsdServiceInfo, tryRegister: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
        ) {
            if (tryRegister) {
                val callback = ServiceInfoCallback(this, serviceInfo)
                serviceInfoCallbacks[serviceInfo.serviceName] = callback
                nsdManager.registerServiceInfoCallback(serviceInfo, Runnable::run, callback)
                return
            } else {
                serviceInfoCallbacks.remove(serviceInfo.serviceName)?.let { callback ->
                    try {
                        nsdManager.unregisterServiceInfoCallback(callback)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        nsdManager.resolveService(serviceInfo, ResolveListener(this))
    }

    private fun onServiceLost(serviceInfo: NsdServiceInfo) {
        val index = serviceInfoList.indexOfFirst { it.serviceName == serviceInfo.serviceName }
        if (index != -1) {
            serviceInfoList.removeAt(index)
            notifyListener()
        }
    }

    private fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        if (_state.value == DiscoveryState.STOPPED || _state.value == DiscoveryState.STOPPING) {
            Timber.w("ServiceType: $serviceTypeFormatted serviceResolved but discovery is stopped")
            return
        }
        val index = serviceInfoList.indexOfFirst { it.serviceName == serviceInfo.serviceName }
        if (index != -1) {
            serviceInfoList[index] = serviceInfo
        } else {
            serviceInfoList.add(serviceInfo)
        }
        notifyListener()
    }

    private fun notifyListener() {
        listener(ArrayList(serviceInfoList))
    }

    private class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Timber.d("ServiceType: $serviceType discoveryStarted in DiscoveryListener")
            adbMdns._state.value = DiscoveryState.STARTED
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.w("ServiceType: $serviceType startDiscoveryFailed errorCode: $errorCode in DiscoveryListener")
            adbMdns._state.value = DiscoveryState.STOPPED
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Timber.d("ServiceType: $serviceType discoveryStopped in DiscoveryListener")
            adbMdns._state.value = DiscoveryState.STOPPED
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Timber.w("ServiceType: $serviceType stopDiscoveryFailed errorCode: $errorCode in DiscoveryListener")
            adbMdns._state.value = DiscoveryState.STOPPED
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    private class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Timber.d("NsdServiceInfo: $serviceInfo errorCode: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(serviceInfo)
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    private class ServiceInfoCallback(private val adbMdns: AdbMdns, val serviceInfo: NsdServiceInfo) : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            adbMdns.onServiceFound(serviceInfo, false)
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(serviceInfo)
        }

        override fun onServiceLost() {
            adbMdns.onServiceLost(serviceInfo)
        }

        override fun onServiceInfoCallbackUnregistered() {
            Timber.d("ServiceInfoCallback unregistered for ${serviceInfo.serviceName}")
        }
    }

    enum class DiscoveryState {
        STOPPED,
        STARTING,
        STARTED,
        STOPPING
    }

    companion object {
        const val SERVICE_TYPE_ADB = "adb"
        const val SERVICE_TYPE_TLS_PAIRING = "adb-tls-pairing"
        const val SERVICE_TYPE_TLS_CONNECT = "adb-tls-connect"

        @StringDef(
            SERVICE_TYPE_ADB,
            SERVICE_TYPE_TLS_PAIRING,
            SERVICE_TYPE_TLS_CONNECT
        )
        @Retention(AnnotationRetention.SOURCE)
        annotation class ServiceType
    }
}
