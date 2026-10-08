package com.dockeep.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.ThemeMode
import com.dockeep.app.ui.DocKeepRoot
import com.dockeep.app.ui.theme.DocKeepTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DocKeepApplication
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        setContent {
            val settings = app.container.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = null as AppSettings?
            ).value
            keepSplash = settings == null
            DocKeepTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM) {
                if (settings == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    DocKeepRoot(
                        activity = this,
                        container = app.container,
                        settings = settings
                    )
                }
            }
        }
    }
}
