// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import timber.log.Timber;

/**
 * This class represents an ADB connection.
 */
// Copyright 2013 Cameron Gutman
public class AdbConnection implements Closeable {
    /**
     * The underlying socket that this class uses to communicate with the target device.
     */
    @NonNull
    private final Socket mSocket;

    @NonNull
    private final String mHost;

    private final int mPort;

    /**
     * The last allocated local stream ID. The ID chosen for the next stream will be this value + 1.
     */
    private int mLastLocalId;

    /**
     * The input stream that this class uses to read from the socket.
     */
    @GuardedBy("lock")
    @NonNull
    private final InputStream mPlainInputStream;

    /**
     * The output stream that this class uses to read from the socket.
     */
    @GuardedBy("lock")
    @NonNull
    private final OutputStream mPlainOutputStream;

    /**
     * The input stream that this class uses to read from the TLS socket.
     */
    @GuardedBy("lock")
    @Nullable
    private volatile InputStream mTlsInputStream;

    /**
     * The output stream that this class uses to read from the TLS socket.
     */
    @GuardedBy("lock")
    @Nullable
    private volatile OutputStream mTlsOutputStream;

    /**
     * The backend thread that handles responding to ADB packets.
     */
    @NonNull
    private final Thread mConnectionThread;

    /**
     * Specifies whether a CNXN has been attempted.
     */
    private volatile boolean mConnectAttempted;

    /**
     * Whether the connection thread should give up if the first authentication attempt fails.
     */
    private volatile boolean mAbortOnUnauthorised;

    /**
     * Whether the first authentication attempt failed and {@link #mAbortOnUnauthorised} was {@code true}.
     */
    private volatile boolean mAuthorisationFailed;

    /**
     * Specifies whether a CNXN packet has been received from the peer.
     */
    private volatile boolean mConnectionEstablished;

    /**
     * Exceptions that occur in {@link #createConnectionThread()}.
     */
    @Nullable
    private volatile Exception mConnectionException;

    /**
     * Specifies the maximum amount data that can be sent to the remote peer.
     * This is only valid after connect() returns successfully.
     */
    private volatile int mMaxData = AdbProtocol.MAX_PAYLOAD;

    private volatile int mProtocolVersion = AdbProtocol.A_VERSION_MIN;

    @NonNull
    private final AdbKeyPair mAdbKeyPair;

    /**
     * Specifies whether this connection has already sent a signed token.
     */
    private volatile boolean mSentSignature;

    /**
     * A hash map of our opened streams indexed by local ID.
     */
    @NonNull
    private final ConcurrentHashMap<Integer, AdbStream> mOpenedStreams;

    private volatile boolean mIsTls = false;

    @GuardedBy("lock")
    @NonNull
    private final Object mLock = new Object();

    /**
     * Creates a AdbConnection object associated with the socket and crypto object specified.
     *
     * @return A new AdbConnection object.
     * @throws IOException If there is a socket error
     */
    @WorkerThread
    @NonNull
    static AdbConnection create(@NonNull String host, int port, @NonNull AdbKeyPair adbKeyPair) throws IOException {
        return new AdbConnection(host, port, adbKeyPair);
    }

    /**
     * Internal constructor to initialize some internal state
     */
    @WorkerThread
    private AdbConnection(@NonNull String host, int port, @NonNull AdbKeyPair adbKeyPair) throws IOException {
        this.mHost = Objects.requireNonNull(host);
        this.mPort = port;
        this.mAdbKeyPair = Objects.requireNonNull(adbKeyPair);
        try {
            this.mSocket = new Socket(host, port);
        } catch (Throwable th) {
            //noinspection UnnecessaryInitCause
            throw (IOException) new IOException().initCause(th);
        }
        this.mPlainInputStream = mSocket.getInputStream();
        this.mPlainOutputStream = mSocket.getOutputStream();

        // Disable Nagle because we're sending tiny packets
        mSocket.setTcpNoDelay(true);

        this.mOpenedStreams = new ConcurrentHashMap<>();
        this.mLastLocalId = 0;
        this.mConnectionThread = createConnectionThread();
    }

    @GuardedBy("lock")
    @NonNull
    private InputStream getInputStream() {
        return mIsTls ? Objects.requireNonNull(mTlsInputStream) : mPlainInputStream;
    }

    @GuardedBy("lock")
    @NonNull
    private OutputStream getOutputStream() {
        return mIsTls ? Objects.requireNonNull(mTlsOutputStream) : mPlainOutputStream;
    }

    /**
     * Creates a new connection thread.
     *
     * @return A new connection thread.
     */
    @NonNull
    private Thread createConnectionThread() {
        return new Thread(() -> {
            loop:
            while (!mConnectionThread.isInterrupted()) {
                try {
                    // Read and parse a message off the socket's input stream
                    AdbProtocol.Message msg = AdbProtocol.Message.parse(getInputStream(), mMaxData);

                    switch (msg.command) {
                        // Stream-oriented commands
                        case AdbProtocol.A_OKAY:
                        case AdbProtocol.A_WRTE:
                        case AdbProtocol.A_CLSE: {
                            // Ignore all packets when not connected
                            if (!mConnectionEstablished) {
                                continue;
                            }

                            // Get the stream object corresponding to the packet
                            AdbStream waitingStream = mOpenedStreams.get(msg.arg1);
                            if (waitingStream == null) {
                                continue;
                            }

                            synchronized (waitingStream) {
                                if (msg.command == AdbProtocol.A_OKAY) {
                                    // We're ready for writes
                                    waitingStream.updateRemoteId(msg.arg0);
                                    waitingStream.readyForWrite();

                                    // Notify an open/write
                                    waitingStream.notify();
                                } else if (msg.command == AdbProtocol.A_WRTE) {
                                    // Got some data from our partner
                                    waitingStream.addPayload(msg.payload);

                                    // Tell it we're ready for more
                                    waitingStream.sendReady();
                                } else { // if (msg.command == AdbProtocol.A_CLSE) {
                                    mOpenedStreams.remove(msg.arg1);
                                    // Notify readers and writers
                                    waitingStream.notifyClose(true);
                                }
                            }
                            break;
                        }
                        case AdbProtocol.A_STLS: {
                            sendPacket(AdbProtocol.generateStls(mProtocolVersion));

                            SSLContext sslContext = SslUtil.getSslContext(mAdbKeyPair);
                            SSLSocket tlsSocket = (SSLSocket) sslContext.getSocketFactory()
                                    .createSocket(mSocket, mHost, mPort, true);
                            tlsSocket.startHandshake();

                            synchronized (AdbConnection.this) {
                                mTlsInputStream = tlsSocket.getInputStream();
                                mTlsOutputStream = tlsSocket.getOutputStream();
                                mIsTls = true;
                            }
                            break;
                        }
                        case AdbProtocol.A_AUTH: {
                            if (mIsTls) {
                                break;
                            }
                            if (msg.arg0 != AdbProtocol.ADB_AUTH_TOKEN) {
                                break;
                            }
                            byte[] packet;
                            // This is an authentication challenge
                            if (mSentSignature) {
                                if (mAbortOnUnauthorised) {
                                    mAuthorisationFailed = true;
                                    break loop;
                                }

                                // We've already tried our signature, so send our public key
                                packet = AdbProtocol.generateAuth(AdbProtocol.ADB_AUTH_RSAPUBLICKEY, AndroidPubkey
                                        .encodeWithName((RSAPublicKey) mAdbKeyPair.getPublicKey(), mAdbKeyPair.getKeyName()), mProtocolVersion);
                            } else {
                                // Sign the token
                                packet = AdbProtocol.generateAuth(AdbProtocol.ADB_AUTH_SIGNATURE, AndroidPubkey
                                        .adbAuthSign(mAdbKeyPair.getPrivateKey(), msg.payload), mProtocolVersion);
                                mSentSignature = true;
                            }

                            // Write the AUTH reply
                            sendPacket(packet);
                            break;
                        }
                        case AdbProtocol.A_CNXN: {
                            synchronized (AdbConnection.this) {
                                mProtocolVersion = msg.arg0;
                                mMaxData = msg.arg1;
                                mConnectionEstablished = true;
                                AdbConnection.this.notifyAll();
                            }
                            break;
                        }
                        case AdbProtocol.A_OPEN:
                        case AdbProtocol.A_SYNC:
                        default:
                            break;
                    }
                } catch (Exception e) {
                    mConnectionException = e;
                    Timber.e(e, "Connection error");
                    // The cleanup is taken care of by a combination of this thread and close()
                    break;
                }
            }

            // This thread takes care of cleaning up pending streams
            synchronized (AdbConnection.this) {
                cleanupStreams();
                AdbConnection.this.notifyAll();
                mConnectionEstablished = false;
                mConnectAttempted = false;
            }
        });
    }

    /**
     * Get the version of the ADB protocol supported by the ADB daemon. In API 29 (Android 9) or later, the daemon
     * returns {@link AdbProtocol#A_VERSION_SKIP_CHECKSUM}. In other cases, it returns {@link AdbProtocol#A_VERSION_MIN}.
     *
     * @see #isConnectionEstablished()
     */
    public int getProtocolVersion() {
        return mProtocolVersion;
    }

    /**
     * Get the max data size supported by the ADB daemon. A connection have to be attempted before calling this method
     * and shall be blocked if the connection is in progress.
     *
     * @return The maximum data size indicated in the CONNECT packet.
     * @throws InterruptedException        If a connection cannot be waited on.
     * @throws IOException                 if the connection fails.
     * @throws AdbPairingRequiredException If ADB lacks pairing
     */
    public int getMaxData() throws InterruptedException, IOException, AdbPairingRequiredException {
        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        return mMaxData;
    }

    /**
     * Whether a connection has been established. A connection has been established if a CONNECT request has been
     * received from the ADB daemon.
     */
    public boolean isConnectionEstablished() {
        return mConnectionEstablished;
    }

    /**
     * Whether the underlying socket is connected to an ADB daemon and is not in a closed state.
     */
    public boolean isConnected() {
        return !mSocket.isClosed() && mSocket.isConnected();
    }

    /**
     * Same as {@link #connect(long, TimeUnit, boolean)} without throwing anything if the first authentication attempt
     * fails.
     *
     * @return {@code true} if the connection was established, or {@code false} if the connection timed out
     * @throws IOException                 If the socket fails while connecting
     * @throws InterruptedException        If timeout has reached
     * @throws AdbPairingRequiredException If ADB lacks pairing
     */
    public boolean connect() throws IOException, InterruptedException, AdbPairingRequiredException {
        return connect(Long.MAX_VALUE, TimeUnit.MILLISECONDS, false);
    }

    /**
     * Connects to the remote device. This routine will block until the connection completes or the timeout elapses.
     *
     * @param timeout             the time to wait for the lock
     * @param unit                the time unit of the timeout argument
     * @param throwOnUnauthorised Whether to throw an {@link AdbAuthenticationFailedException}
     *                            if the peer rejects out first authentication attempt
     * @return {@code true} if the connection was established, or {@code false} if the connection timed out
     * @throws IOException                      If the socket fails while connecting
     * @throws InterruptedException             If timeout has reached
     * @throws AdbAuthenticationFailedException If {@code throwOnUnauthorised} is {@code true} and the peer rejects the
     *                                          first authentication attempt, which indicates that the peer has not
     *                                          saved the public key from a previous connection
     * @throws AdbPairingRequiredException      If ADB lacks pairing
     */
    public boolean connect(long timeout, @NonNull TimeUnit unit, boolean throwOnUnauthorised)
            throws IOException, InterruptedException, AdbAuthenticationFailedException, AdbPairingRequiredException {
        if (mConnectionEstablished) {
            throw new IllegalStateException("Already connected");
        }

        // Send CONNECT
        sendPacket(AdbProtocol.generateConnect(mProtocolVersion));

        // Start the connection thread to respond to the peer
        mConnectAttempted = true;
        mAbortOnUnauthorised = throwOnUnauthorised;
        mAuthorisationFailed = false;
        mConnectionThread.start();

        return waitForConnection(timeout, Objects.requireNonNull(unit));
    }

    /**
     * Opens an {@link AdbStream} object corresponding to the specified destination.
     * This routine will block until the connection completes.
     *
     * @param service The service to open. One of the services under {@link LocalServices.Services}.
     * @param args    Additional arguments supported by the service (see the corresponding constant to learn more).
     * @return AdbStream object corresponding to the specified destination
     * @throws UnsupportedEncodingException If the destination cannot be encoded to UTF-8
     * @throws IOException                  If the stream fails while sending the packet
     * @throws InterruptedException         If we are unable to wait for the connection to finish
     * @throws AdbPairingRequiredException  If ADB lacks pairing
     */
    @NonNull
    public AdbStream open(@LocalServices.Services int service, @NonNull String... args)
            throws IOException, InterruptedException, AdbPairingRequiredException {
        if (service < LocalServices.SERVICE_FIRST || service > LocalServices.SERVICE_LAST) {
            throw new IllegalArgumentException("Invalid service: " + service);
        }
        return open(LocalServices.getDestination(service, args));
    }

    /**
     * Opens an AdbStream object corresponding to the specified destination.
     * This routine will block until the connection completes.
     *
     * @param destination The destination to open on the target
     * @return AdbStream object corresponding to the specified destination
     * @throws UnsupportedEncodingException If the destination cannot be encoded to UTF-8
     * @throws IOException                  If the stream fails while sending the packet
     * @throws InterruptedException         If we are unable to wait for the connection to finish
     * @throws AdbPairingRequiredException  If ADB lacks pairing
     */
    @NonNull
    public AdbStream open(@NonNull String destination)
            throws IOException, InterruptedException, AdbPairingRequiredException {
        int localId = ++mLastLocalId;

        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        // Add this stream to this list of half-open streams
        AdbStream stream = new AdbStream(this, localId);
        mOpenedStreams.put(localId, stream);

        // Send OPEN
        sendPacket(AdbProtocol.generateOpen(localId, Objects.requireNonNull(destination), mProtocolVersion));

        // Wait for the connection thread to receive the OKAY
        synchronized (stream) {
            stream.wait();
        }

        // Check if the OPEN request was rejected
        if (stream.isClosed()) {
            mOpenedStreams.remove(localId);
            throw new ConnectException("Stream open actively rejected by remote peer.");
        }

        return stream;
    }

    private boolean waitForConnection(long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, IOException, AdbPairingRequiredException {
        synchronized (this) {
            // Block if a connection is pending, but not yet complete
            long timeoutEndMillis = System.currentTimeMillis() + Objects.requireNonNull(unit).toMillis(timeout);
            while (!mConnectionEstablished && mConnectAttempted && timeoutEndMillis - System.currentTimeMillis() > 0) {
                wait(timeoutEndMillis - System.currentTimeMillis());
            }

            if (!mConnectionEstablished) {
                if (mConnectAttempted) {
                    return false;
                } else if (mAuthorisationFailed) {
                    // The peer may not have saved the public key in the past connections, or they've been removed.
                    throw new AdbAuthenticationFailedException();
                } else {
                    Exception connectionException = mConnectionException;
                    if (connectionException != null) {
                        if (connectionException instanceof javax.net.ssl.SSLProtocolException) {
                            String message = connectionException.getMessage();
                            if (message != null && message.contains("protocol error")) {
                                throw (AdbPairingRequiredException) (new AdbPairingRequiredException("ADB pairing is required.").initCause(connectionException));
                            }
                        }
                    }
                    throw new IOException("Connection failed");
                }
            }
        }

        return true;
    }

    /**
     * This function terminates all I/O on streams associated with this ADB connection
     */
    private void cleanupStreams() {
        // Close all streams on this connection
        for (AdbStream s : mOpenedStreams.values()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        mOpenedStreams.clear();
    }

    /**
     * This routine closes the Adb connection and underlying socket
     *
     * @throws IOException if the socket fails to close
     */
    @Override
    public void close() throws IOException {
        // Closing the socket will kick the connection thread
        mSocket.close();

        // Wait for the connection thread to die
        mConnectionThread.interrupt();
        try {
            mConnectionThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    void sendPacket(byte[] packet) throws IOException {
        synchronized (mLock) {
            OutputStream os = getOutputStream();
            os.write(packet);
            os.flush();
        }
    }

    void flushPacket() throws IOException {
        synchronized (mLock) {
            getOutputStream().flush();
        }
    }
}
