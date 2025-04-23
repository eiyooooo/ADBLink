// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.eiyooooo.adblink.entity.Preferences;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * This class represents an ADB connection.
 */
// Copyright 2013 Cameron Gutman
public class AdbConnection implements Closeable {

    /**
     * The underlying channel to the ADB daemon.
     */
    @NonNull
    private final AdbChannel mChannel;

    /**
     * The last allocated local stream ID. The ID chosen for the next stream will be this value + 1.
     */
    private int mLastLocalId;

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
     * Specifies whether the connection is closing.
     */
    private volatile boolean mClosing = false;

    /**
     * Specifies the maximum amount data that can be sent to the remote peer.
     * This is only valid after connect() returns successfully.
     */
    private volatile int mMaxData = AdbProtocol.MAX_PAYLOAD;

    /**
     * Specifies the protocol version of the ADB daemon.
     * This is only valid after connect() returns successfully.
     */
    private volatile int mProtocolVersion = AdbProtocol.A_VERSION_MIN;

    private volatile List<String> mSupportedFeatures;

    /**
     * Specifies whether delayed ACK is enabled. This is only valid after connect() returns successfully.
     */
    private volatile boolean mEnableDelayedAck;

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

    /**
     * Creates a AdbConnection object that connects to the specified host and port.
     *
     * @return A new AdbConnection object.
     * @throws IOException If there is a channel error
     */
    @WorkerThread
    @NonNull
    static AdbConnection create(@NonNull String host, int port, @NonNull AdbKeyPair adbKeyPair) throws IOException {
        TcpChannel channel = new TcpChannel(host, port);
        return new AdbConnection(channel, adbKeyPair);
    }

    /**
     * Creates a AdbConnection object that connects to the specified USB device.
     *
     * @return A new AdbConnection object.
     * @throws IOException If there is a channel error
     */
    @WorkerThread
    @NonNull
    static AdbConnection create(@NonNull UsbManager usbManager, @NonNull UsbDevice usbDevice, @NonNull AdbKeyPair adbKeyPair) throws IOException {
        UsbChannel channel = new UsbChannel(usbManager, usbDevice);
        return new AdbConnection(channel, adbKeyPair);
    }

    /**
     * Internal constructor to initialize some internal state
     */
    @WorkerThread
    private AdbConnection(@NonNull AdbChannel channel, @NonNull AdbKeyPair adbKeyPair) {
        this.mEnableDelayedAck = Preferences.INSTANCE.getEnableDelayedAck();

        this.mChannel = Objects.requireNonNull(channel);
        this.mAdbKeyPair = Objects.requireNonNull(adbKeyPair);

        this.mOpenedStreams = new ConcurrentHashMap<>();
        this.mLastLocalId = 0;
        this.mConnectionThread = createConnectionThread();
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
                    // Read and parse a message over the channel
                    AdbProtocol.Message msg = AdbProtocol.Message.parse(mChannel, mMaxData);

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
                                    Integer ackedBytes = null;
                                    byte[] payload = msg.payload;
                                    if (payload != null) {
                                        if (payload.length == 4) {
                                            ackedBytes = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).getInt();
                                        } else if (payload.length != 0) {
                                            throw new Exception("invalid A_OKAY payload size: " + payload.length);
                                        }
                                    }

                                    if (waitingStream.getRemoteId() == 0) {
                                        waitingStream.setRemoteId(msg.arg0);
                                        // Check if we can continue sending data
                                        waitingStream.processAck(ackedBytes);
                                        // Notify an open
                                        waitingStream.notifyAll();
                                    } else if (waitingStream.getRemoteId() == msg.arg0) {
                                        waitingStream.processAck(ackedBytes);
                                    } else {
                                        throw new Exception("invalid A_OKAY stream ID: " + msg.arg0);
                                    }
                                } else if (msg.command == AdbProtocol.A_WRTE) {
                                    // Got some data from our partner
                                    waitingStream.addPayload(msg.payload);
                                    // Tell it we're ready for more
                                    if (mEnableDelayedAck) {
                                        waitingStream.sendReady(msg.payload.length);
                                    } else {
                                        waitingStream.sendReady();
                                    }
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

                            if (mChannel instanceof TcpChannel) {
                                ((TcpChannel) mChannel).upgradeTls(mAdbKeyPair);
                            }
                            break;
                        }
                        case AdbProtocol.A_AUTH: {
                            if (mChannel instanceof TcpChannel && ((TcpChannel) mChannel).isTls()) {
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
                                mSupportedFeatures = parseBanner(msg.payload);
                                if (mEnableDelayedAck && mSupportedFeatures != null) {
                                    mEnableDelayedAck = mSupportedFeatures.contains("delayed_ack");
                                } else {
                                    mEnableDelayedAck = false;
                                }
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
                    if (!mClosing) {
                        mConnectionException = e;
                        Timber.e(e, "Connection error");
                    }
                    // The cleanup is taken care of by a combination of this thread and close()
                    break;
                }
            }

            // This thread takes care of cleaning up pending streams
            synchronized (AdbConnection.this) {
                cleanupStreams();
                AdbConnection.this.notifyAll();
                mClosing = false;
                mConnectionEstablished = false;
                mConnectAttempted = false;
            }
        });
    }

    /**
     * Parses the banner from the ADB daemon.
     *
     * @param bannerBytes The bytes of the banner.
     * @return A list of supported features.
     */
    private List<String> parseBanner(byte[] bannerBytes) {
        String banner = new String(bannerBytes, StandardCharsets.UTF_8);
        Timber.d("parseBanner: %s", banner);

        String[] pieces = banner.split(":");

        List<String> features = new ArrayList<>();

        if (pieces.length > 2) {
            String props = pieces[2];
            String[] propArray = props.split(";");

            for (String prop : propArray) {
                if (prop.isEmpty()) continue;

                String[] keyValue = prop.split("=");
                if (keyValue.length != 2) continue;

                String key = keyValue[0];
                String value = keyValue[1];

                if (key.equals("features")) {
                    String[] featureArray = value.split(",");
                    features.addAll(Arrays.asList(featureArray));
                }
            }
        }

        return features;
    }

    /**
     * Get the version of the ADB protocol supported by the ADB daemon. In API 29 (Android 9) or later, the daemon
     * returns {@link AdbProtocol#A_VERSION_SKIP_CHECKSUM}. In other cases, it returns {@link AdbProtocol#A_VERSION_MIN}.
     *
     * @return The protocol version indicated in the CONNECT packet.
     * @throws InterruptedException        If a connection cannot be waited on.
     * @throws IOException                 if the connection fails.
     * @throws AdbPairingRequiredException If ADB lacks pairing
     * @see #isConnectionEstablished()
     */
    int getProtocolVersion() throws InterruptedException, IOException, AdbPairingRequiredException {
        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

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
     * @see #isConnectionEstablished()
     */
    public int getMaxData() throws InterruptedException, IOException, AdbPairingRequiredException {
        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        return mMaxData;
    }

    /**
     * Check if the given feature is supported by the ADB daemon.
     *
     * @param feature The feature to check.
     * @return {@code true} if the feature is supported, {@code false} otherwise.
     * @throws InterruptedException        If a connection cannot be waited on.
     * @throws IOException                 if the connection fails.
     * @throws AdbPairingRequiredException If ADB lacks pairing
     */
    boolean hasFeature(String feature) throws InterruptedException, IOException, AdbPairingRequiredException {
        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        return mSupportedFeatures != null && mSupportedFeatures.contains(feature);
    }

    /**
     * Get if delayed ACK is enabled. A connection have to be attempted before calling this method and shall be blocked
     * if the connection is in progress.
     *
     * @return {@code true} if delayed ACK is enabled, {@code false} otherwise.
     * @throws InterruptedException        If a connection cannot be waited on.
     * @throws IOException                 if the connection fails.
     * @throws AdbPairingRequiredException If ADB lacks pairing
     * @see #isConnectionEstablished()
     */
    public boolean isEnableDelayedAck() throws InterruptedException, IOException, AdbPairingRequiredException {
        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        return mEnableDelayedAck;
    }

    /**
     * Whether a connection has been established. A connection has been established if a CONNECT request has been
     * received from the ADB daemon.
     *
     * @return {@code true} if a connection has been established, {@code false} otherwise.
     */
    public boolean isConnectionEstablished() {
        return mConnectionEstablished;
    }

    /**
     * Whether the underlying channel is connected to an ADB daemon and is not in a closed state.
     *
     * @return {@code true} if the underlying channel is connected, {@code false} otherwise.
     */
    public boolean isConnected() {
        return mChannel.isConnected();
    }

    /**
     * Same as {@link #connect(long, TimeUnit, boolean)} without throwing anything if the first authentication attempt
     * fails.
     *
     * @return {@code true} if the connection was established, or {@code false} if the connection timed out
     * @throws IOException                 If the channel fails while connecting
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
     * @throws IOException                      If the channel fails while connecting
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
        sendPacket(AdbProtocol.generateConnect(mProtocolVersion, mEnableDelayedAck));

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
        boolean enableDelayedAck = mEnableDelayedAck;

        if (!mConnectAttempted) {
            throw new IllegalStateException("connect() must be called first");
        }

        waitForConnection(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        // Add this stream to this list of half-open streams
        AdbStream stream = new AdbStream(this, localId);
        mOpenedStreams.put(localId, stream);

        // Enable delayed ACK if supported
        if (enableDelayedAck) {
            stream.enableDelayedAck();
        }

        // Send OPEN
        sendPacket(AdbProtocol.generateOpen(localId, Objects.requireNonNull(destination), mProtocolVersion, enableDelayedAck));

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

    /**
     * Waits for the connection to be established.
     *
     * @param timeout The time to wait for the lock
     * @param unit    The time unit of the timeout argument
     * @return {@code true} if the connection was established, or {@code false} if the connection timed out
     * @throws InterruptedException        If a connection cannot be waited on
     * @throws IOException                 If the connection fails
     * @throws AdbPairingRequiredException If ADB lacks pairing
     */
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
     * Sends a packet over the channel.
     *
     * @param packet The packet to send.
     * @throws IOException If the channel fails while sending the packet
     */
    void sendPacket(byte[] packet) throws IOException {
        mChannel.write(packet);
        mChannel.flush();
    }

    /**
     * Flushes the packet to the channel.
     *
     * @throws IOException If the channel fails while flushing the packet
     */
    void flushPacket() throws IOException {
        mChannel.flush();
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
     * This routine closes the Adb connection and underlying channel
     *
     * @throws IOException if the channel fails to close
     */
    @Override
    public void close() throws IOException {
        mClosing = true;

        // Closing the channel will kick the connection thread
        mChannel.close();

        // Wait for the connection thread to die
        mConnectionThread.interrupt();
        try {
            mConnectionThread.join();
        } catch (InterruptedException ignored) {
        }
    }
}
