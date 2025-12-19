package com.eiyooooo.adblink.adb

import android.graphics.Bitmap
import android.graphics.Color
import com.eiyooooo.adblink.adb.discover.AdbDiscoverService
import com.eiyooooo.adblink.adb.discover.AdbDiscoverServiceType
import com.eiyooooo.adblink.adb.discover.DiscoveredDevice
import com.eiyooooo.adblink.adb.discover.DiscoveredDeviceManager
import com.eiyooooo.adblink.application
import com.eiyooooo.adblink.data.ConnectionSession
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.util.QrCodeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

object AdbManager {

    var initialized = false
        private set

    private val adbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var adbKeyPair: AdbKeyPair

    private var tcpConnectDiscoverService: AdbDiscoverService? = null
    private var tlsConnectDiscoverService: AdbDiscoverService? = null
    private var tlsPairingDiscoverService: AdbDiscoverService? = null

    private val discoveredDeviceManager = DiscoveredDeviceManager(adbScope)
    val discoveredConnectDevices: StateFlow<List<DiscoveredDevice>>
        get() = discoveredDeviceManager.discoveredConnectDevices
    val discoveredPairingDevices: StateFlow<List<DiscoveredDevice>>
        get() = discoveredDeviceManager.discoveredPairingDevices

    private lateinit var adbConnectionManager: AdbConnectionManager
    val connectionSessions: StateFlow<Map<String, ConnectionSession>>
        get() = adbConnectionManager.connectionSessions

    private lateinit var adbPairingManager: AdbPairingManager
    val qrPairingSuccess: StateFlow<Boolean>
        get() = adbPairingManager.qrPairingSuccess

    fun init(): Boolean {
        if (initialized) {
            return true
        }

        try {
            adbKeyPair = AdbKeyPair.loadKeyPair(application.filesDir) ?: AdbKeyPair.createAdbKeyPair(application.filesDir)

            adbPairingManager = AdbPairingManager(adbKeyPair, adbScope)

            tcpConnectDiscoverService = AdbDiscoverService(AdbDiscoverServiceType.ADB_TCP) { infos ->
                Timber.d("Discovered device: $infos")
                discoveredDeviceManager.handleDiscoveredConnectDevices(infos, AdbDiscoverServiceType.ADB_TCP)
            }

            tlsConnectDiscoverService = AdbDiscoverService(AdbDiscoverServiceType.ADB_TLS_CONNECT) { infos ->
                Timber.d("Discovered connect service: $infos")
                discoveredDeviceManager.handleDiscoveredConnectDevices(infos, AdbDiscoverServiceType.ADB_TLS_CONNECT)
            }

            tlsPairingDiscoverService = AdbDiscoverService(AdbDiscoverServiceType.ADB_TLS_PAIRING) { infos ->
                Timber.d("Discovered pairing service: $infos")
                adbPairingManager.pairWithDiscoveredService(infos)
                discoveredDeviceManager.handleDiscoveredPairingDevices(infos)
            }

            adbConnectionManager = AdbConnectionManager(adbKeyPair, adbScope)

            initialized = true

            if (Preferences.enableAdbDiscoverService) {
                setDiscoverServicesEnabled(true)
            }
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AdbManager")
            return false
        }
    }

    fun setDiscoverServicesEnabled(enabled: Boolean) {
        if (!initialized) {
            return
        }

        if (enabled) {
            tcpConnectDiscoverService?.start()
            tlsConnectDiscoverService?.start()
            tlsPairingDiscoverService?.start()
        } else {
            tcpConnectDiscoverService?.stop()
            tlsConnectDiscoverService?.stop()
            tlsPairingDiscoverService?.stop()
        }
    }

    fun getAdbKeyPairName(): String = adbKeyPair.keyName

    fun recreateAdbKeyPair() {
        adbKeyPair = AdbKeyPair.recreateAdbKeyPair(application.filesDir)
    }

    fun pair(host: String, port: Int, pairingCode: String) = adbPairingManager.pair(host, port, pairingCode)

    fun resetQrPairingSuccess() = adbPairingManager.resetQrPairingSuccess()

    fun createPairingQrCode(
        size: Int = QrCodeGenerator.DEFAULT_SIZE,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.TRANSPARENT
    ): Bitmap = adbPairingManager.createPairingQrCode(size, foregroundColor, backgroundColor)

    fun connectDevice(device: Device) = adbConnectionManager.connectDevice(device)

    fun reconnectDevice(device: Device) = adbConnectionManager.reconnectDevice(device)

    fun disconnectDevice(deviceUuid: String) = adbConnectionManager.disconnectDevice(deviceUuid)

    fun removeDiscoveredConnectDevice(deviceSerial: String) = discoveredDeviceManager.removeDiscoveredConnectDevice(deviceSerial)
}
