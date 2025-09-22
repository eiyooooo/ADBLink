package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo

enum class AdbDiscoverServiceType(val value: String) {
    ADB_TCP("_adb._tcp"),
    ADB_TLS_CONNECT("_adb-tls-connect._tcp"),
    ADB_TLS_PAIRING("_adb-tls-pairing._tcp");

    companion object {
        fun fromNsdServiceInfo(serviceType: NsdServiceInfo): AdbDiscoverServiceType? {
            return entries.find { serviceType.serviceType?.contains(it.value) == true }
        }
    }
}
