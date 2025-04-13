package com.eiyooooo.adblink.adb

import android.graphics.Bitmap
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import com.eiyooooo.adblink.application
import com.eiyooooo.adblink.util.QrCodeGenerator
import com.eiyooooo.adblink.util.generateRandomString
import com.eiyooooo.adblink.util.isReachableLocallySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.charset.StandardCharsets

object AdbManager {

    var initialized = false
        private set

    private val adbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var adbKeyPair: AdbKeyPair

    private var adbMdns: AdbMdns? = null
    private var tlsPairingMdns: AdbMdns? = null
    private var tlsConnectMdns: AdbMdns? = null

    private var qrPairInfo: Pair<String, String>? = null

    fun init(): Boolean {
        if (initialized) {
            return true
        }

        try {
            adbKeyPair = AdbKeyPair.loadKeyPair(application.filesDir) ?: AdbKeyPair.createAdbKeyPair(application.filesDir)

            adbMdns = AdbMdns(AdbMdns.SERVICE_TYPE_ADB) {
                Timber.d("Discovered device: $it")
            }.apply {
                start()
            }

            tlsPairingMdns = AdbMdns(AdbMdns.SERVICE_TYPE_TLS_PAIRING) { infos ->
                Timber.d("Discovered pairing service: $infos")
                pairWithDiscoveredService(infos)
            }.apply {
                start()
            }

            tlsConnectMdns = AdbMdns(AdbMdns.SERVICE_TYPE_TLS_CONNECT) {
                Timber.d("Discovered connect service: $it")
            }.apply {
                start()
            }

            initialized = true
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AdbManager")
            return false
        }
    }

    fun recreateAdbKeyPair() {
        adbKeyPair = AdbKeyPair.recreateAdbKeyPair(application.filesDir)
    }

    fun pair(host: String, port: Int, pairingCode: String): Boolean {
        return try {
            AdbPairingConnection(
                host,
                port,
                pairingCode.toByteArray(StandardCharsets.UTF_8),
                adbKeyPair
            ).use { pairingClient ->
                pairingClient.start()
            }
            Timber.i("Successfully paired with device $host:$port")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to pair with device $host:$port")
            false
        }
    }

    fun createPairingQrCode(): Bitmap {
        val instanceName = "ADBLink-" + generateRandomString(8)
        val pairingCode = generateRandomString(12)
        qrPairInfo = instanceName to pairingCode
        val pairText = "WIFI:T:ADB;S:$instanceName;P:$pairingCode;;"
        return QrCodeGenerator.encodeQrCodeToBitmap(pairText)
    }

    private fun pairWithDiscoveredService(infos: List<NsdServiceInfo>) {
        val currentQrPairInfo = qrPairInfo ?: return
        infos.firstOrNull { it.serviceName == currentQrPairInfo.first }?.let { info ->
            adbScope.launch {
                var targetAddress: String? = null
                val pairingCode = currentQrPairInfo.second

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
                ) {
                    for (address in info.hostAddresses) {
                        if (address.isReachableLocallySuspend()) {
                            targetAddress = address.hostAddress ?: continue
                            break
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val host = info.host
                    if (host != null && host.isReachableLocallySuspend()) {
                        targetAddress = host.hostAddress
                    }
                }

                targetAddress?.let {
                    Timber.d("Trying to pair with device $it:${info.port} via QR code")
                    val result = withContext(Dispatchers.IO) {
                        pair(it, info.port, pairingCode)
                    }
                    if (result) {
                        qrPairInfo = null
                    }
                }
            }
        }
    }
}
