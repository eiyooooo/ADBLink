// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class abstracts the underlying ADB streams
 */
// Copyright 2013 Cameron Gutman
public class AdbStream implements Closeable {

    /**
     * The AdbConnection object that the stream communicates over
     */
    private final AdbConnection mAdbConnection;

    /**
     * The local ID of the stream
     */
    private final int mLocalId;

    /**
     * The remote ID of the stream
     */
    private volatile int mRemoteId = 0;

    /**
     * Indicates whether WRTE is currently allowed
     */
    private final AtomicBoolean mWriteReady = new AtomicBoolean(false);

    /**
     * Lock for the write operation
     */
    private final Object mWriteLock = new Object();

    /**
     * For Delayed Ack: Tracks how many bytes the remote peer has acknowledged
     * we can send. Null if delayed ack is disabled.
     * Needs careful synchronization.
     */
    private volatile Long mAvailableSendBytes = null;

    /**
     * A queue of data from the target's WRTE packets
     */
    private final Queue<byte[]> mReadQueue = new ConcurrentLinkedQueue<>();

    /**
     * Store data received from the first WRTE packet in order to support buffering.
     */
    private final ByteBuffer mReadBuffer;

    /**
     * Indicates whether the connection is closed already
     */
    private volatile boolean mIsClosed = false;

    /**
     * Whether the remote peer has closed but we still have unread data in the queue
     */
    private volatile boolean mPendingClose = false;

    /**
     * Creates a new AdbStream object on the specified AdbConnection
     * with the given local ID.
     *
     * @param adbConnection AdbConnection that this stream is running on
     * @param localId       Local ID of the stream
     */
    AdbStream(AdbConnection adbConnection, int localId)
            throws IOException, InterruptedException, AdbPairingRequiredException {
        this.mAdbConnection = adbConnection;
        this.mLocalId = localId;
        this.mReadBuffer = (ByteBuffer) ByteBuffer.allocate(adbConnection.getMaxData()).flip();
    }

    /**
     * Creates a new AdbInputStream object on this stream
     *
     * @return A new AdbInputStream object
     */
    public AdbInputStream openInputStream() {
        return new AdbInputStream(this);
    }

    /**
     * Creates a new AdbOutputStream object on this stream
     *
     * @return A new AdbOutputStream object
     */
    public AdbOutputStream openOutputStream() {
        return new AdbOutputStream(this);
    }

    /**
     * Called by the connection thread to indicate newly received data.
     *
     * @param payload Data inside the WRTE message
     */
    void addPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return;
        }
        synchronized (mReadQueue) {
            mReadQueue.add(payload);
            mReadQueue.notifyAll();
        }
    }

    /**
     * Called by the connection thread to send an OKAY packet, allowing the
     * other side to continue transmission.
     *
     * @throws IOException                 If the connection fails while sending the packet
     * @throws AdbPairingRequiredException If pairing is required
     * @throws InterruptedException        If the thread is interrupted
     */
    void sendReady() throws IOException, AdbPairingRequiredException, InterruptedException {
        // Generate and send a OKAY packet
        mAdbConnection.sendPacket(AdbProtocol.generateReady(mLocalId, mRemoteId, mAdbConnection.getProtocolVersion()));
    }

    /**
     * Sends an OKAY/READY packet to the peer, indicating that the peer can
     * continue sending data. This is used for delayed ACK mode.
     *
     * @param ackBytes Bytes consumed by the local reader since the last READY/ACK.
     * @throws IOException                 If the connection fails while sending the packet
     * @throws AdbPairingRequiredException If pairing is required
     * @throws InterruptedException        If the thread is interrupted
     */
    void sendReady(int ackBytes) throws IOException, AdbPairingRequiredException, InterruptedException {
        // Generate and send a OKAY packet
        mAdbConnection.sendPacket(AdbProtocol.generateReady(mLocalId, mRemoteId, mAdbConnection.getProtocolVersion(), ackBytes));
    }

    /**
     * Called by the connection thread to set the remote ID for this stream
     *
     * @param remoteId New remote ID
     */
    void setRemoteId(int remoteId) {
        this.mRemoteId = remoteId;
    }

    /**
     * Returns the remote ID of this stream
     *
     * @return The remote ID of this stream
     */
    int getRemoteId() {
        return mRemoteId;
    }

    /**
     * Enables delayed ACK mode for this stream. This allows the peer to send
     * data without waiting for an ACK.
     */
    void enableDelayedAck() {
        if (mAvailableSendBytes == null) {
            mAvailableSendBytes = 0L;
        }
    }

    /**
     * Processes acknowledgments received by the local socket.The method updates the
     * available send bytes and notifies waiting threads when more data can be written.
     *
     * @param ackedBytes The number of bytes acknowledged, or null if not using payload acknowledgment
     * @throws IOException If there's a mismatch between socket and payload acknowledgment modes
     */
    void processAck(Integer ackedBytes) throws IOException {
        if (mPendingClose || mIsClosed) {
            return;
        }

        boolean socketHasAck = mAvailableSendBytes != null;
        boolean payloadHasAck = ackedBytes != null;

        if (socketHasAck != payloadHasAck) {
            throw new IOException("delayed ack mismatch: socket = " + socketHasAck + ", payload = " + payloadHasAck);
        }

        synchronized (mWriteLock) {
            if (socketHasAck) {
                long available = mAvailableSendBytes;
                int ackValue = ackedBytes;

                long newAvailable = available + ackValue;
                mAvailableSendBytes = newAvailable;

                if (newAvailable > 0) {
                    mWriteLock.notifyAll();
                }
            } else {
                mWriteReady.set(true);
                mWriteLock.notifyAll();
            }
        }
    }

    /**
     * Read bytes from the ADB daemon.
     *
     * @return the next byte of data, or {@code -1} if the end of the stream is reached.
     * @throws IOException If the stream fails while waiting
     */
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null || length <= 0) {
            return 0;
        }

        if (mReadBuffer.hasRemaining()) {
            return readBuffer(bytes, offset, length);
        }

        if (mIsClosed) {
            return -1;
        }

        // Buffer has no data, grab from the queue
        synchronized (mReadQueue) {
            byte[] data;
            // Wait for the connection to close or data to be received
            while ((data = mReadQueue.poll()) == null && !mIsClosed) {
                try {
                    mReadQueue.wait();
                } catch (InterruptedException e) {
                    //noinspection UnnecessaryInitCause
                    throw (IOException) new IOException().initCause(e);
                }
            }

            if (mIsClosed) {
                return -1;
            }

            if (mPendingClose && mReadQueue.isEmpty()) {
                // The peer closed the stream, and we've finished reading the stream data, so this stream is finished
                mIsClosed = true;
            }

            // Add data to the buffer
            if (data != null) {
                mReadBuffer.clear();
                mReadBuffer.put(data);
                mReadBuffer.flip();
                if (mReadBuffer.hasRemaining()) {
                    return readBuffer(bytes, offset, length);
                }
            }
        }

        return 0;
    }

    /**
     * Reads bytes from the read buffer.
     *
     * @param bytes  The byte array to read into
     * @param offset The offset in the byte array to start reading into
     * @param length The number of bytes to read
     * @return The number of bytes read
     */
    private int readBuffer(byte[] bytes, int offset, int length) {
        int count = 0;
        for (int i = offset; i < offset + length; ++i) {
            if (mReadBuffer.hasRemaining()) {
                bytes[i] = mReadBuffer.get();
                ++count;
            }
        }
        return count;
    }

    /**
     * Sends a WRTE packet with a given byte array payload. It does not flush the stream.
     *
     * @param bytes Payload in the form of a byte array
     * @throws IOException If the stream fails while sending data
     */
    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null || length <= 0) {
            return;
        }

        if (mPendingClose || mIsClosed) {
            throw new IOException("Stream closed");
        }

        synchronized (mWriteLock) {
            if (mAvailableSendBytes != null) {
                while (!mPendingClose && !mIsClosed && mAvailableSendBytes <= 0) {
                    try {
                        mWriteLock.wait();
                    } catch (InterruptedException e) {
                        //noinspection UnnecessaryInitCause
                        throw (IOException) new IOException().initCause(e);
                    }
                }
            } else {
                while (!mPendingClose && !mIsClosed && !mWriteReady.compareAndSet(true, false)) {
                    try {
                        mWriteLock.wait();
                    } catch (InterruptedException e) {
                        //noinspection UnnecessaryInitCause
                        throw (IOException) new IOException().initCause(e);
                    }
                }
            }

            if (mPendingClose || mIsClosed) {
                throw new IOException("Stream closed");
            }
        }

        // Split and send data as WRTE packet
        int maxData;
        try {
            maxData = mAdbConnection.getMaxData();
        } catch (InterruptedException | AdbPairingRequiredException e) {
            //noinspection UnnecessaryInitCause
            throw (IOException) new IOException().initCause(e);
        }

        int remainingLength = length;
        int currentOffset = offset;

        while (remainingLength > 0) {
            int toSend;

            synchronized (mWriteLock) {
                if (mPendingClose || mIsClosed) {
                    throw new IOException("Stream closed");
                }

                if (mAvailableSendBytes != null) {
                    toSend = (int) Math.min(remainingLength, Math.min(maxData, mAvailableSendBytes));
                    mAvailableSendBytes -= toSend;
                } else {
                    toSend = Math.min(remainingLength, maxData);
                }
            }

            if (mPendingClose || mIsClosed) {
                throw new IOException("Stream closed");
            }

            try {
                mAdbConnection.sendPacket(AdbProtocol.generateWrite(mLocalId, mRemoteId, bytes, currentOffset, toSend, mAdbConnection.getProtocolVersion()));
            } catch (InterruptedException | AdbPairingRequiredException e) {
                //noinspection UnnecessaryInitCause
                throw (IOException) new IOException().initCause(e);
            }

            currentOffset += toSend;
            remainingLength -= toSend;

            if (mAvailableSendBytes != null) {
                synchronized (mWriteLock) {
                    if (mAvailableSendBytes <= 0 && remainingLength > 0) {
                        while (!mPendingClose && !mIsClosed && mAvailableSendBytes <= 0) {
                            try {
                                mWriteLock.wait();
                            } catch (InterruptedException e) {
                                //noinspection UnnecessaryInitCause
                                throw (IOException) new IOException().initCause(e);
                            }
                        }

                        if (mPendingClose || mIsClosed) {
                            throw new IOException("Stream closed");
                        }
                    }
                }
            }
        }
    }

    public void flush() throws IOException {
        if (mPendingClose || mIsClosed) {
            throw new IOException("Stream closed");
        }
        mAdbConnection.flushPacket();
    }

    /**
     * Called by the connection thread to notify that the stream was closed by the peer.
     */
    void notifyClose(boolean closedByPeer) {
        // We don't call close() because it sends another CLSE
        if (closedByPeer && !mReadQueue.isEmpty()) {
            // The remote peer closed the stream, but we haven't finished reading the remaining data
            mPendingClose = true;
        } else {
            mIsClosed = true;
        }

        // Notify stream openers/readers/writers that the stream is closed
        synchronized (this) {
            notifyAll();
        }
        synchronized (mWriteLock) {
            mWriteLock.notifyAll();
        }
        synchronized (mReadQueue) {
            mReadQueue.notifyAll();
        }
    }

    /**
     * Closes the stream. This sends a close message to the peer.
     *
     * @throws IOException If the stream fails while sending the close message.
     */
    @Override
    public void close() throws IOException {
        synchronized (this) {
            // This may already be closed by the remote host
            if (mPendingClose || mIsClosed) {
                return;
            }

            // Notify stream openers/readers/writers that we've closed
            notifyClose(false);
        }

        try {
            mAdbConnection.sendPacket(AdbProtocol.generateClose(mLocalId, mRemoteId, mAdbConnection.getProtocolVersion()));
        } catch (InterruptedException | AdbPairingRequiredException e) {
            //noinspection UnnecessaryInitCause
            throw (IOException) new IOException().initCause(e);
        }
    }

    /**
     * Returns whether the stream is closed or not
     *
     * @return True if the stream is close, false if not
     */
    public boolean isClosed() {
        return mIsClosed;
    }

    /**
     * Returns an estimate of available data.
     *
     * @return an estimate of the number of bytes that can be read from this stream without blocking.
     * @throws IOException if the stream is close.
     */
    public int available() throws IOException {
        synchronized (this) {
            if (mIsClosed) {
                throw new IOException("Stream closed");
            }
            if (mReadBuffer.hasRemaining()) {
                return mReadBuffer.remaining();
            }
            byte[] data = mReadQueue.peek();
            return data == null ? 0 : data.length;
        }
    }
}
