package com.dockeep.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.domain.ThemeMode
import com.dockeep.app.ui.DocKeepRoot
import com.dockeep.app.ui.theme.DocKeepTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private val showContent = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !showContent.get() }
        setTheme(R.style.Theme_DocKeep)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DocKeepApplication
        setContent {
            var timedOut by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(3_000)
                timedOut = true
            }
            val loaded = app.container.settingsRepository.settings
                .map<AppSettings, AppSettings?> { it }
                .collectAsStateWithLifecycle(initialValue = null)
                .value
            val settings = loaded ?: if (timedOut) AppSettings() else null
            LaunchedEffect(settings) {
                if (settings != null) showContent.set(true)
            }
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
