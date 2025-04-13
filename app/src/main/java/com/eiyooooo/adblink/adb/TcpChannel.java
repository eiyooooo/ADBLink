// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public class TcpChannel implements AdbChannel {

    @NonNull
    private final Socket mSocket;

    @NonNull
    private final String mHost;

    private final int mPort;

    @GuardedBy("mLock")
    @NonNull
    private final InputStream mPlainInputStream;

    @GuardedBy("mLock")
    @NonNull
    private final OutputStream mPlainOutputStream;

    @GuardedBy("mLock")
    @Nullable
    private volatile InputStream mTlsInputStream;

    @GuardedBy("mLock")
    @Nullable
    private volatile OutputStream mTlsOutputStream;

    volatile boolean mIsTls = false;

    @GuardedBy("mLock")
    @NonNull
    private final Object mLock = new Object();

    @WorkerThread
    public TcpChannel(@NonNull String host, int port) throws IOException {
        this.mHost = Objects.requireNonNull(host);
        this.mPort = port;
        this.mSocket = new Socket(host, port);
        this.mPlainInputStream = mSocket.getInputStream();
        this.mPlainOutputStream = mSocket.getOutputStream();
        mSocket.setTcpNoDelay(true);
    }

    @GuardedBy("mLock")
    @NonNull
    private InputStream getInputStream() {
        return mIsTls ? Objects.requireNonNull(mTlsInputStream) : mPlainInputStream;
    }

    @GuardedBy("mLock")
    @NonNull
    private OutputStream getOutputStream() {
        return mIsTls ? Objects.requireNonNull(mTlsOutputStream) : mPlainOutputStream;
    }

    public void upgradeTls(@NonNull AdbKeyPair adbKeyPair) throws Exception {
        SSLContext sslContext = SslUtil.getSslContext(adbKeyPair);
        SSLSocket tlsSocket = (SSLSocket) sslContext.getSocketFactory()
                .createSocket(mSocket, mHost, mPort, true);
        tlsSocket.startHandshake();

        synchronized (mLock) {
            mTlsInputStream = tlsSocket.getInputStream();
            mTlsOutputStream = tlsSocket.getOutputStream();
            mIsTls = true;
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        synchronized (mLock) {
            getOutputStream().write(data);
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized (mLock) {
            getOutputStream().flush();
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        synchronized (mLock) {
            return getInputStream().read(buffer, offset, length);
        }
    }

    @Override
    public void close() throws IOException {
        mSocket.close();
    }

    @Override
    public boolean isConnected() {
        return !mSocket.isClosed() && mSocket.isConnected();
    }
}
