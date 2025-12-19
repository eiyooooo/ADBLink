package com.eiyooooo.adblink.adb;

import androidx.annotation.Nullable;

interface AdbConnectionListener {

    void onAbnormalClosed(@Nullable Exception exception);
}
