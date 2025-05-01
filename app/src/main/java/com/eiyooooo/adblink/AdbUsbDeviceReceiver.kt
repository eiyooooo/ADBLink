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
import com.eiyooooo.adblink.data.Device
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.entity.Preferences
import com.eiyooooo.adblink.entity.SystemServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

enum class UsbAuthorizationStatus {
    NO_DEVICES,
    PERMISSION_NEEDED,
    ALL_AUTHORIZED
}

class AdbUsbDeviceReceiver private constructor() : BroadcastReceiver() {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.eiyooooo.adblink.USB_PERMISSION"

        val INSTANCE: AdbUsbDeviceReceiver by lazy { AdbUsbDeviceReceiver() }
    }

    private val usbScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _authorizationStatus = MutableStateFlow(UsbAuthorizationStatus.NO_DEVICES)
    val authorizationStatus: StateFlow<UsbAuthorizationStatus> = _authorizationStatus

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
        checkConnectedUsbDevice(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!Preferences.enableUSB) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }?.let { usbDevice ->
            val action = intent.action
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    requestUsbPermissionIfNeeded(context, usbDevice)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbScope.launch {
                        val allDevices = DeviceRepository.devices.first()
                        for (device in allDevices) {
                            if (device.usbDevice != null &&
                                device.usbDevice.vendorId == usbDevice.vendorId &&
                                device.usbDevice.productId == usbDevice.productId
                            ) {
                                Timber.d("USB device detached: ${device.deviceName}, UUID: ${device.uuid}")
                                DeviceRepository.updateUsbDevice(device.uuid, null)
                                break
                            }
                        }
                    }
                }

                ACTION_USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        processAuthorizedUsbDevice(usbDevice)
                    } else {
                        Timber.w("USB permission denied for device: ${usbDevice.deviceName}")
                    }
                }
            }
            refreshAuthorizationStatus()
        }
    }

    private fun processAuthorizedUsbDevice(usbDevice: UsbDevice) {
        usbDevice.serialNumber?.let { serialNumber ->
            Timber.d("USB permission granted for device: ${usbDevice.deviceName}, UUID: $serialNumber")
            usbScope.launch {
                val existingDevice = DeviceRepository.devices.first().find {
                    it.deviceSerial == serialNumber
                }
                if (existingDevice == null) {
                    val manufacturer = usbDevice.manufacturerName ?: ""
                    val productName = usbDevice.productName ?: ""
                    val device = Device.createWithDefaults(
                        deviceBrand = manufacturer,
                        deviceName = productName,
                        deviceSerial = serialNumber,
                        usbDevice = usbDevice,
                        tcpHostPort = null,
                        tlsName = null,
                        tlsHostPort = null
                    )
                    DeviceRepository.addOrUpdateDevice(device)
                    Timber.d("Added device to repository via USB: $serialNumber")
                } else {
                    DeviceRepository.updateUsbDevice(existingDevice.uuid, usbDevice)
                    Timber.d("Updated device in repository via USB: $serialNumber")
                }
            }
        } ?: run {
            Timber.w("USB device serial number is null, cannot process device: ${usbDevice.deviceName}")
            return
        }
    }

    private fun requestUsbPermissionIfNeeded(context: Context, usbDevice: UsbDevice?) {
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
        SystemServices.usbManager.deviceList.let { deviceList ->
            deviceList.values.forEach {
                requestUsbPermissionIfNeeded(context, it)
            }
            refreshAuthorizationStatus(deviceList)
        }
    }

    private fun refreshAuthorizationStatus(deviceList: Map<String, UsbDevice> = SystemServices.usbManager.deviceList) {
        var permissionNeeded = false
        var potentialDeviceFound = false
        deviceList.values.forEach {
            if (AdbManager.isPotentialAdbDevice(it)) {
                potentialDeviceFound = true
                if (!SystemServices.usbManager.hasPermission(it)) {
                    permissionNeeded = true
                    return@forEach
                }
            }
        }
        _authorizationStatus.value = if (permissionNeeded) {
            UsbAuthorizationStatus.PERMISSION_NEEDED
        } else if (potentialDeviceFound) {
            UsbAuthorizationStatus.ALL_AUTHORIZED
        } else {
            UsbAuthorizationStatus.NO_DEVICES
        }
    }

    fun resetUsbConnections() {
        DeviceRepository.clearAllUsbDevices()
        _authorizationStatus.value = UsbAuthorizationStatus.NO_DEVICES
    }
}
