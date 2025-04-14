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

    @NonNull
    private final InputStream mPlainInputStream;

    @Nullable
    private volatile InputStream mTlsInputStream = null;

    @GuardedBy("mWriteLock")
    @NonNull
    private final OutputStream mPlainOutputStream;

    @GuardedBy("mWriteLock")
    @Nullable
    private volatile OutputStream mTlsOutputStream = null;

    @GuardedBy("mWriteLock")
    @NonNull
    private final Object mWriteLock = new Object();

    @WorkerThread
    public TcpChannel(@NonNull String host, int port) throws IOException {
        this.mHost = Objects.requireNonNull(host);
        this.mPort = port;
        this.mSocket = new Socket(host, port);
        this.mPlainInputStream = mSocket.getInputStream();
        this.mPlainOutputStream = mSocket.getOutputStream();
        mSocket.setTcpNoDelay(true);
    }

    @NonNull
    private InputStream getInputStream() {
        return mTlsInputStream != null ? Objects.requireNonNull(mTlsInputStream) : mPlainInputStream;
    }

    @GuardedBy("mWriteLock")
    @NonNull
    private OutputStream getOutputStream() {
        return mTlsOutputStream != null ? Objects.requireNonNull(mTlsOutputStream) : mPlainOutputStream;
    }

    public void upgradeTls(@NonNull AdbKeyPair adbKeyPair) throws Exception {
        SSLContext sslContext = SslUtil.getSslContext(adbKeyPair);
        SSLSocket tlsSocket = (SSLSocket) sslContext.getSocketFactory()
                .createSocket(mSocket, mHost, mPort, true);
        tlsSocket.startHandshake();
        synchronized (mWriteLock) {
            mTlsInputStream = tlsSocket.getInputStream();
            mTlsOutputStream = tlsSocket.getOutputStream();
        }
    }

    public boolean isTls() {
        return mTlsInputStream != null && mTlsOutputStream != null;
    }

    @Override
    public void write(byte[] data) throws IOException {
        synchronized (mWriteLock) {
            getOutputStream().write(data);
        }
    }

    @Override
    public void flush() throws IOException {
        synchronized (mWriteLock) {
            getOutputStream().flush();
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return getInputStream().read(buffer, offset, length);
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
