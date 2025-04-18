// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class UsbChannel implements AdbChannel {

    private final UsbDeviceConnection mDeviceConnection;

    private final UsbInterface usbInterface;

    private final UsbEndpoint mEndpointIn;

    private final UsbEndpoint mEndpointOut;

    private final LinkedList<UsbRequest> mInRequestPool = new LinkedList<>();

    private final AtomicBoolean mConnected = new AtomicBoolean(true);

    private final Object mWriteLock = new Object();

    public UsbChannel(@NonNull UsbManager usbManager, @NonNull UsbDevice usbDevice) throws IOException {
        mDeviceConnection = usbManager.openDevice(usbDevice);
        if (mDeviceConnection == null) {
            throw new IOException("Failed to open USB device");
        }

        UsbInterface foundInterface = null;
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface tmpUsbInterface = usbDevice.getInterface(i);
            if ((tmpUsbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) &&
                    (tmpUsbInterface.getInterfaceSubclass() == 66) &&
                    (tmpUsbInterface.getInterfaceProtocol() == 1)) {
                foundInterface = tmpUsbInterface;
                break;
            }
        }

        if (foundInterface == null) {
            mDeviceConnection.close();
            throw new IOException("ADB interface not found");
        }

        usbInterface = foundInterface;

        if (!mDeviceConnection.claimInterface(usbInterface, true)) {
            mDeviceConnection.close();
            throw new IOException("Failed to claim interface");
        }

        UsbEndpoint inEndpoint = null;
        UsbEndpoint outEndpoint = null;

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    outEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                }
            }
        }

        if (inEndpoint == null || outEndpoint == null) {
            mDeviceConnection.releaseInterface(usbInterface);
            mDeviceConnection.close();
            throw new IOException("USB endpoints not found");
        }

        mEndpointIn = inEndpoint;
        mEndpointOut = outEndpoint;
    }

    @Override
    public void write(byte[] data) throws IOException {
        synchronized (mWriteLock) {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            while (buffer.remaining() > 0) {
                byte[] header = new byte[AdbProtocol.ADB_HEADER_LENGTH];
                buffer.get(header);
                if (mDeviceConnection.bulkTransfer(mEndpointOut, header, header.length, 1000) < 0) {
                    throw new IOException("Failed to send header");
                }

                int payloadLength = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
                if (payloadLength > 0) {
                    byte[] payload = new byte[payloadLength];
                    buffer.get(payload);
                    if (mDeviceConnection.bulkTransfer(mEndpointOut, payload, payload.length, 1000) < 0) {
                        throw new IOException("Failed to send payload");
                    }
                }
            }
        }
    }

    public void releaseInRequest(UsbRequest request) {
        synchronized (mInRequestPool) {
            mInRequestPool.add(request);
        }
    }

    public UsbRequest getInRequest() {
        synchronized (mInRequestPool) {
            if (mInRequestPool.isEmpty()) {
                UsbRequest request = new UsbRequest();
                request.initialize(mDeviceConnection, mEndpointIn);
                return request;
            } else {
                return mInRequestPool.removeFirst();
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        UsbRequest usbRequest = getInRequest();

        ByteBuffer expected = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        usbRequest.setClientData(expected);

        if (!usbRequest.queue(expected, length)) {
            throw new IOException("fail to queue read UsbRequest");
        }

        while (true) {
            UsbRequest wait = mDeviceConnection.requestWait();

            if (wait == null) {
                throw new IOException("Connection.requestWait return null");
            }

            ByteBuffer clientData = (ByteBuffer) wait.getClientData();
            wait.setClientData(null);

            if (wait.getEndpoint() == mEndpointOut) {
                // a write UsbRequest complete, just ignore
            } else if (expected == clientData) {
                releaseInRequest(wait);
                break;
            } else {
                throw new IOException("unexpected behavior");
            }
        }
        expected.flip();
        int readBytes = expected.remaining();
        if (offset + readBytes > buffer.length) {
            throw new IOException("Buffer overflow: offset + readBytes > buffer.length");
        }
        expected.get(buffer, offset, readBytes);
        return readBytes;
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws IOException {
        mConnected.set(false);
        mDeviceConnection.releaseInterface(usbInterface);
        mDeviceConnection.close();
    }

    @Override
    public boolean isConnected() {
        return mConnected.get();
    }
}
