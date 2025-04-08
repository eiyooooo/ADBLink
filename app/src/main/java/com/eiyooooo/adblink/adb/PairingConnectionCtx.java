// SPDX-License-Identifier: GPL-3.0-or-later OR Apache-2.0

package com.eiyooooo.adblink.adb;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import timber.log.Timber;

public final class PairingConnectionCtx implements Closeable {

    public static final String EXPORTED_KEY_LABEL = "adb-label\u0000";
    public static final int EXPORT_KEY_SIZE = 64;

    private enum State {
        Ready,
        ExchangingMsgs,
        ExchangingPeerInfo,
        Stopped
    }

    enum Role {
        Client,
        Server,
    }

    private final String mHost;
    private final int mPort;
    private final byte[] mPswd;
    private final PeerInfo mPeerInfo;
    private final SSLContext mSslContext;
    private final Role mRole = Role.Client;

    private DataInputStream mInputStream;
    private DataOutputStream mOutputStream;
    private PairingAuthCtx mPairingAuthCtx;
    private State mState = State.Ready;

    PairingConnectionCtx(@NonNull String host, int port, @NonNull byte[] pswd, @NonNull AdbKeyPair adbKeyPair, @NonNull String deviceName)
            throws NoSuchAlgorithmException, KeyManagementException, InvalidKeyException {
        this.mHost = Objects.requireNonNull(host);
        this.mPort = port;
        this.mPswd = Objects.requireNonNull(pswd);
        this.mPeerInfo = new PeerInfo(PeerInfo.ADB_RSA_PUB_KEY, AndroidPubkey.encodeWithName((RSAPublicKey)
                adbKeyPair.getPublicKey(), Objects.requireNonNull(deviceName)));
        this.mSslContext = SslUtil.getSslContext(adbKeyPair);
    }

    public void start() throws IOException {
        if (mState != State.Ready) {
            throw new IOException("Connection is not ready yet.");
        }

        mState = State.ExchangingMsgs;

        // Start worker
        setupTlsConnection();

        for (; ; ) {
            switch (mState) {
                case ExchangingMsgs:
                    if (!doExchangeMsgs()) {
                        notifyResult();
                        throw new IOException("Exchanging message wasn't successful.");
                    }
                    mState = State.ExchangingPeerInfo;
                    break;
                case ExchangingPeerInfo:
                    if (!doExchangePeerInfo()) {
                        notifyResult();
                        throw new IOException("Could not exchange peer info.");
                    }
                    notifyResult();
                    return;
                case Ready:
                case Stopped:
                    throw new IOException("Connection closed with errors.");
            }
        }
    }

    private void notifyResult() {
        mState = State.Stopped;
    }

    private void setupTlsConnection() throws IOException {
        Socket socket;
        if (mRole == Role.Server) {
            SSLServerSocket sslServerSocket = (SSLServerSocket) mSslContext.getServerSocketFactory().createServerSocket(mPort);
            socket = sslServerSocket.accept();
        } else { // role == Role.Client
            socket = new Socket(mHost, mPort);
        }
        socket.setTcpNoDelay(true);

        // We use custom SSLContext to allow any SSL certificates
        SSLSocket sslSocket = (SSLSocket) mSslContext.getSocketFactory().createSocket(socket, mHost, mPort, true);
        sslSocket.startHandshake();
        Timber.d("Handshake succeeded.");

        mInputStream = new DataInputStream(sslSocket.getInputStream());
        mOutputStream = new DataOutputStream(sslSocket.getOutputStream());

        // To ensure the connection is not stolen while we do the PAKE, append the exported key material from the
        // tls connection to the password.
        byte[] keyMaterial = exportKeyingMaterial(sslSocket, EXPORT_KEY_SIZE);
        byte[] passwordBytes = new byte[mPswd.length + keyMaterial.length];
        System.arraycopy(mPswd, 0, passwordBytes, 0, mPswd.length);
        System.arraycopy(keyMaterial, 0, passwordBytes, mPswd.length, keyMaterial.length);

        PairingAuthCtx pairingAuthCtx = PairingAuthCtx.createAlice(passwordBytes);
        if (pairingAuthCtx == null) {
            throw new IOException("Unable to create PairingAuthCtx.");
        }
        this.mPairingAuthCtx = pairingAuthCtx;
    }

    @SuppressLint("PrivateApi") // Conscrypt is a stable private API
    private byte[] exportKeyingMaterial(SSLSocket sslSocket, int length) throws SSLException {
        // Conscrypt#exportKeyingMaterial(SSLSocket socket, String label, byte[] context, int length): byte[]
        //          throws SSLException
        try {
            Class<?> conscryptClass;
            if (SslUtil.isCustomConscrypt()) {
                conscryptClass = Class.forName("org.conscrypt.Conscrypt");
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // Although support for conscrypt has been added in Android 5.0 (Lollipop),
                // TLS1.3 isn't supported until Android 9 (Pie).
                throw new SSLException("TLSv1.3 isn't supported on your platform. Use custom Conscrypt library instead.");
            } else {
                conscryptClass = Class.forName("com.android.org.conscrypt.Conscrypt");
            }
            Method exportKeyingMaterial = conscryptClass.getMethod("exportKeyingMaterial", SSLSocket.class,
                    String.class, byte[].class, int.class);
            return (byte[]) exportKeyingMaterial.invoke(null, sslSocket, EXPORTED_KEY_LABEL, null, length);
        } catch (SSLException e) {
            throw e;
        } catch (Throwable th) {
            throw new SSLException(th);
        }
    }

    private void writeHeader(@NonNull PairingPacketHeader header, @NonNull byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(PairingPacketHeader.PAIRING_PACKET_HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        header.writeTo(buffer);

        mOutputStream.write(buffer.array());
        mOutputStream.write(payload);
    }

    @Nullable
    private PairingPacketHeader readHeader() throws IOException {
        byte[] bytes = new byte[PairingPacketHeader.PAIRING_PACKET_HEADER_SIZE];
        mInputStream.readFully(bytes);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return PairingPacketHeader.readFrom(buffer);
    }

    @NonNull
    private PairingPacketHeader createHeader(byte type, int payloadSize) {
        return new PairingPacketHeader(PairingPacketHeader.CURRENT_KEY_HEADER_VERSION, type, payloadSize);
    }

    private boolean checkHeaderType(byte expected, byte actual) {
        if (expected != actual) {
            Timber.e("Unexpected header type (expected=" + expected + " actual=" + actual + ")");
            return false;
        }
        return true;
    }

    private boolean doExchangeMsgs() throws IOException {
        byte[] msg = mPairingAuthCtx.getMsg();

        PairingPacketHeader ourHeader = createHeader(PairingPacketHeader.SPAKE2_MSG, msg.length);
        // Write our SPAKE2 msg
        writeHeader(ourHeader, msg);

        // Read the peer's SPAKE2 msg header
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null || !checkHeaderType(PairingPacketHeader.SPAKE2_MSG, theirHeader.type)) return false;

        // Read the SPAKE2 msg payload and initialize the cipher for encrypting the PeerInfo and certificate.
        byte[] theirMsg = new byte[theirHeader.payloadSize];
        mInputStream.readFully(theirMsg);

        try {
            return mPairingAuthCtx.initCipher(theirMsg);
        } catch (Exception e) {
            Timber.e("Unable to initialize pairing cipher");
            //noinspection UnnecessaryInitCause
            throw (IOException) new IOException().initCause(e);
        }
    }

    private boolean doExchangePeerInfo() throws IOException {
        // Encrypt PeerInfo
        ByteBuffer buffer = ByteBuffer.allocate(PeerInfo.MAX_PEER_INFO_SIZE).order(ByteOrder.BIG_ENDIAN);
        mPeerInfo.writeTo(buffer);
        byte[] outBuffer = mPairingAuthCtx.encrypt(buffer.array());
        if (outBuffer == null) {
            Timber.e("Failed to encrypt peer info");
            return false;
        }

        // Write out the packet header
        PairingPacketHeader ourHeader = createHeader(PairingPacketHeader.PEER_INFO, outBuffer.length);
        // Write out the encrypted payload
        writeHeader(ourHeader, outBuffer);

        // Read in the peer's packet header
        PairingPacketHeader theirHeader = readHeader();
        if (theirHeader == null || !checkHeaderType(PairingPacketHeader.PEER_INFO, theirHeader.type)) return false;

        // Read in the encrypted peer certificate
        byte[] theirMsg = new byte[theirHeader.payloadSize];
        mInputStream.readFully(theirMsg);

        // Try to decrypt the certificate
        byte[] decryptedMsg = mPairingAuthCtx.decrypt(theirMsg);
        if (decryptedMsg == null) {
            Timber.e("Unsupported payload while decrypting peer info.");
            return false;
        }

        // The decrypted message should contain the PeerInfo.
        if (decryptedMsg.length != PeerInfo.MAX_PEER_INFO_SIZE) {
            Timber.e("Got size=" + decryptedMsg.length + " PeerInfo.size=" + PeerInfo.MAX_PEER_INFO_SIZE);
            return false;
        }

        PeerInfo theirPeerInfo = PeerInfo.readFrom(ByteBuffer.wrap(decryptedMsg));
        Timber.d(theirPeerInfo.toString());
        return true;
    }

    @Override
    public void close() {
        Arrays.fill(mPswd, (byte) 0);
        try {
            mInputStream.close();
        } catch (IOException ignore) {
        }
        try {
            mOutputStream.close();
        } catch (IOException ignore) {
        }
        if (mState != State.Ready) {
            mPairingAuthCtx.destroy();
        }
    }

    private static class PeerInfo {
        public static final int MAX_PEER_INFO_SIZE = 1 << 13;

        public static final byte ADB_RSA_PUB_KEY = 0;
        public static final byte ADB_DEVICE_GUID = 0;

        @NonNull
        public static PeerInfo readFrom(@NonNull ByteBuffer buffer) {
            byte type = buffer.get();
            byte[] data = new byte[MAX_PEER_INFO_SIZE - 1];
            buffer.get(data);
            return new PeerInfo(type, data);
        }

        private final byte type;
        private final byte[] data = new byte[MAX_PEER_INFO_SIZE - 1];

        public PeerInfo(byte type, byte[] data) {
            this.type = type;
            System.arraycopy(data, 0, this.data, 0, Math.min(data.length, MAX_PEER_INFO_SIZE - 1));
        }

        public void writeTo(@NonNull ByteBuffer buffer) {
            buffer.put(type).put(data);
        }

        @NonNull
        @Override
        public String toString() {
            return "PeerInfo{" +
                    "type=" + type +
                    ", data=" + Arrays.toString(data) +
                    '}';
        }
    }

    private record PairingPacketHeader(byte version, byte type, int payloadSize) {
        public static final byte CURRENT_KEY_HEADER_VERSION = 1;
        public static final byte MIN_SUPPORTED_KEY_HEADER_VERSION = 1;
        public static final byte MAX_SUPPORTED_KEY_HEADER_VERSION = 1;

        public static final int MAX_PAYLOAD_SIZE = 2 * PeerInfo.MAX_PEER_INFO_SIZE;
        public static final byte PAIRING_PACKET_HEADER_SIZE = 6;

        public static final byte SPAKE2_MSG = 0;
        public static final byte PEER_INFO = 1;

        @Nullable
        public static PairingPacketHeader readFrom(@NonNull ByteBuffer buffer) {
            byte version = buffer.get();
            byte type = buffer.get();
            int payload = buffer.getInt();
            if (version < MIN_SUPPORTED_KEY_HEADER_VERSION || version > MAX_SUPPORTED_KEY_HEADER_VERSION) {
                Timber.e("PairingPacketHeader version mismatch (us=" + CURRENT_KEY_HEADER_VERSION
                        + " them=" + version + ")");
                return null;
            }
            if (type != SPAKE2_MSG && type != PEER_INFO) {
                Timber.e("Unknown PairingPacket type %s", type);
                return null;
            }
            if (payload <= 0 || payload > MAX_PAYLOAD_SIZE) {
                Timber.e("Header payload not within a safe payload size (size=" + payload + ")");
                return null;
            }
            return new PairingPacketHeader(version, type, payload);
        }

        public void writeTo(@NonNull ByteBuffer buffer) {
            buffer.put(version).put(type).putInt(payloadSize);
        }

        @NonNull
        @Override
        public String toString() {
            return "PairingPacketHeader{" +
                    "version=" + version +
                    ", type=" + type +
                    ", payloadSize=" + payloadSize +
                    '}';
        }
    }
}
