package com.eiyooooo.adblink.config

import android.content.ClipboardManager
import android.hardware.usb.UsbManager
import android.net.nsd.NsdManager
import androidx.core.content.getSystemService
import com.eiyooooo.adblink.application

object SystemServices {

    val clipboardManager: ClipboardManager by lazy { application.getSystemService()!! }

    val nsdManager: NsdManager by lazy { application.getSystemService()!! }

    val usbManager: UsbManager by lazy { application.getSystemService()!! }
}
