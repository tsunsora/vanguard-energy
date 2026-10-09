package app.vanguard.energy

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class MainActivity : ComponentActivity() {
    private val model: EnergyViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            val session = model.session
            if (session == null) Box(Modifier.fillMaxSize().background(Color(0xff101217)), contentAlignment = Alignment.Center) {
                Text("Vanguard Energy", color = Color(0xffc9f76f))
            } else {
                BackHandler {
                    when {
                        session.setupOpen -> session.showSetup(false)
                        session.menuOpen -> session.showMenu(false)
                        else -> finish()
                    }
                }
                EnergyApp(session)
            }
        }
    }
    override fun onPause() { model.session?.cancelGestures(); super.onPause() }
}

class EnergyViewModel(application: Application) : AndroidViewModel(application) {
    var session by mutableStateOf<TrackerSession?>(null)
        private set
    private val store = AndroidPreferenceStore(application)
    private var cancelMigration: (() -> Unit)? = null
    init {
        if (store.read() != null) session = TrackerSession(store)
        else cancelMigration = readLegacyPreferences(application) { legacy ->
            val preferences = legacy?.let { PreferenceCodec.migrateLegacy(it.first, it.second) } ?: Preferences()
            session = TrackerSession(store, preferences).also {
                if (legacy != null) it.savePreferences() else it.migrationUnavailable()
            }
        }
    }
    override fun onCleared() { cancelMigration?.invoke(); super.onCleared() }
}
