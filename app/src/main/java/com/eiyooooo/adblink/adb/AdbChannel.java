// SPDX-License-Identifier: BSD-3-Clause AND (GPL-3.0-or-later OR Apache-2.0)

package com.eiyooooo.adblink.adb;

import java.io.Closeable;
import java.io.IOException;

public interface AdbChannel extends Closeable {

    void write(byte[] data) throws IOException;

    void flush() throws IOException;

    int read(byte[] buffer, int offset, int length) throws IOException;

    @Override
    void close() throws IOException;

    boolean isConnected();
}
