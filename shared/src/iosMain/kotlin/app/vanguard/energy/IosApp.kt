@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.vanguard.energy

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

private class IosPreferenceStore : PreferenceStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(): String? = defaults.stringForKey("vanguard-native-v1")
    override fun write(value: String): Boolean {
        defaults.setObject(value, forKey = "vanguard-native-v1")
        return defaults.stringForKey("vanguard-native-v1") == value
    }
}

/** Swift only hosts this shared Kotlin UI and forwards foreground/background events. */
class IosApp {
    private val session = TrackerSession(IosPreferenceStore())
    fun viewController(): UIViewController = ComposeUIViewController { EnergyApp(session) }
    fun setActive(active: Boolean) {
        session.cancelGestures()
        UIApplication.sharedApplication.idleTimerDisabled = active
    }
}
