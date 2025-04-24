package com.eiyooooo.adblink

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.eiyooooo.adblink.adb.AdbManager
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.entity.SystemServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class MyBroadcastReceiver private constructor() : BroadcastReceiver() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.eiyooooo.adblink.USB_PERMISSION"

        val INSTANCE: MyBroadcastReceiver by lazy { MyBroadcastReceiver() }
    }

    private val usbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val usbDevices = mutableMapOf<String, UsbDevice>()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(this, filter)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }?.let { usbDevice ->
            val action = intent.action
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (Preferences.enableUSB) {
                        checkUsbDevicePermission(context, usbDevice)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    for ((uuid, device) in usbDevices.entries) {
                        if (device.vendorId == usbDevice.vendorId && device.productId == usbDevice.productId) {
                            Timber.d("USB device detached: ${device.deviceName}, UUID: $uuid")
                            usbScope.launch {
                                AdbManager.removeConnection(uuid)
                            }
                            usbDevices.remove(uuid)
                            break
                        }
                    }
                }

                ACTION_USB_PERMISSION -> {
                    if (Preferences.enableUSB) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            processAuthorizedUsbDevice(usbDevice)
                        } else {
                            Timber.w("USB permission denied for device: ${usbDevice.deviceName}")
                        }
                    }
                }
            }
        }
    }

    private fun processAuthorizedUsbDevice(usbDevice: UsbDevice) {
        usbDevice.serialNumber?.let {
            Timber.d("USB permission granted for device: ${usbDevice.deviceName}, UUID: $it")
            usbDevices[it] = usbDevice
            //TODO: connect adb
        } ?: run {
            Timber.w("USB device serial number is null, cannot process device: ${usbDevice.deviceName}")
            return
        }
    }

    private fun checkUsbDevicePermission(context: Context, usbDevice: UsbDevice?) {
        usbDevice?.takeIf {
            AdbManager.isPotentialAdbDevice(it)
        }?.let {
            if (SystemServices.usbManager.hasPermission(it)) {
                processAuthorizedUsbDevice(it)
            } else {
                val usbPermissionIntent = Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                }
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    usbPermissionIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                )
                SystemServices.usbManager.requestPermission(it, permissionIntent)
            }
        }
    }

    fun checkConnectedUsbDevice(context: Context) {
        SystemServices.usbManager.deviceList.values.forEach {
            checkUsbDevicePermission(context, it)
        }
    }
}
