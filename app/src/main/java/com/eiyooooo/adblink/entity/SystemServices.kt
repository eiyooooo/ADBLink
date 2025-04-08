package com.eiyooooo.adblink.entity

import android.content.ClipboardManager
import android.net.nsd.NsdManager
import androidx.core.content.getSystemService
import com.eiyooooo.adblink.application

object SystemServices {

    val clipboardManager: ClipboardManager by lazy { application.getSystemService()!! }

    val nsdManager: NsdManager by lazy { application.getSystemService()!! }
}
