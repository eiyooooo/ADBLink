package com.eiyooooo.adblink.entity

import android.content.ClipboardManager
import androidx.core.content.getSystemService
import com.eiyooooo.adblink.application

object SystemServices {

    val clipboardManager: ClipboardManager by lazy { application.getSystemService()!! }
}
