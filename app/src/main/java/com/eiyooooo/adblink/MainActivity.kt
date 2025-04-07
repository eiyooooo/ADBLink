package com.eiyooooo.adblink

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.navigation.compose.rememberNavController
import com.eiyooooo.adblink.ui.navigation.MainScaffold
import com.eiyooooo.adblink.ui.theme.AppTheme
import com.eiyooooo.adblink.util.LanguageUtil

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val navController = rememberNavController()
                val windowSizeClass = calculateWindowSizeClass(this)
                MainScaffold(navController, windowSizeClass)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.setLocale(newBase))
    }
}
