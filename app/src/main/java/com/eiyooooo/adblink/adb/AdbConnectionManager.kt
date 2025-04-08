// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package com.eiyooooo.adblink.adb

import androidx.annotation.WorkerThread
import java.io.Closeable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

internal class AdbConnectionManager(private val mAdbKeyPair: AdbKeyPair) : Closeable {

    private val mLock = Object()
    private var mAdbConnection: AdbConnection? = null
    private var mHostAddress = "127.0.0.1"
    private var mTimeout = Long.MAX_VALUE
    private var mTimeoutUnit = TimeUnit.MILLISECONDS
    private var mThrowOnUnauthorised = false

    private val isConnected: Boolean
        get() {
            synchronized(mLock) {
                return mAdbConnection != null && mAdbConnection!!.isConnected && mAdbConnection!!.isConnectionEstablished
            }
        }

    @WorkerThread
    fun connect(port: Int): Boolean {
        synchronized(mLock) {
            if (isConnected) {
                return false
            }
            mAdbConnection = AdbConnection.create(mHostAddress, port, mAdbKeyPair)
            return mAdbConnection!!.connect(mTimeout, mTimeoutUnit, mThrowOnUnauthorised)
        }
    }

    @WorkerThread
    fun connect(host: String, port: Int): Boolean {
        synchronized(mLock) {
            if (isConnected) {
                return false
            }
            mHostAddress = host
            mAdbConnection = AdbConnection.create(host, port, mAdbKeyPair)
            return mAdbConnection!!.connect(mTimeout, mTimeoutUnit, mThrowOnUnauthorised)
        }
    }

    fun disconnect() {
        synchronized(mLock) {
            mAdbConnection?.let {
                it.close()
                mAdbConnection = null
            }
        }
    }

    @WorkerThread
    fun openStream(destination: String): AdbStream {
        synchronized(mLock) {
            mAdbConnection?.let { connection ->
                if (connection.isConnected) {
                    try {
                        return connection.open(destination)
                    } catch (e: AdbPairingRequiredException) {
                        throw IllegalStateException(e)
                    }
                }
            }
            throw IOException("Not connected to ADB.")
        }
    }

    fun openStream(@LocalServices.Services service: Int, vararg args: String): AdbStream {
        synchronized(mLock) {
            mAdbConnection?.let { connection ->
                if (connection.isConnected) {
                    try {
                        return connection.open(service, *args)
                    } catch (e: AdbPairingRequiredException) {
                        throw IllegalStateException(e)
                    }
                }
            }
            throw IOException("Not connected to ADB.")
        }
    }

    @WorkerThread
    fun pair(port: Int, pairingCode: String): Boolean {
        return pair(mHostAddress, port, pairingCode)
    }

    @WorkerThread
    fun pair(host: String, port: Int, pairingCode: String): Boolean {
        synchronized(mLock) {
            PairingConnectionCtx(
                host,
                port,
                pairingCode.toByteArray(StandardCharsets.UTF_8),
                mAdbKeyPair,
                mAdbKeyPair.keyName
            ).use { pairingClient ->
                pairingClient.start()
            }
            return true
        }
    }

    override fun close() {
        mAdbConnection?.let {
            it.close()
            mAdbConnection = null
        }
    }
}
