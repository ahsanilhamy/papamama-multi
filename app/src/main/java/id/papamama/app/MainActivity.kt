package id.papamama.app

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.papamama.app.core.CheckWorker
import id.papamama.app.core.Notifier
import id.papamama.app.data.Store
import id.papamama.app.ui.PapaMamaRoot
import id.papamama.app.ui.PapaTheme
import kotlinx.coroutines.flow.MutableStateFlow

class PapaMamaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.ensureChannels(this)
    }
}

class MainActivity : ComponentActivity() {
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Notifier.ensureChannels(this)
        CheckWorker.schedule(this, Store.state.value.settings.checkHours)
        if (savedInstanceState == null) pendingIntent.value = intent
        setContent {
            PapaTheme {
                PapaMamaRoot(pendingIntent) { pendingIntent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }
}
