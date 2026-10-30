package moe.shizuku.manager.app

import android.content.res.Resources.Theme
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import moe.shizuku.manager.R
import rikka.core.res.isNight
import rikka.material.app.MaterialActivity

abstract class AppActivity : MaterialActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val isDark = resources.configuration.isNight()
        val barStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        super.onCreate(savedInstanceState)
    }

    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun computeUserThemeKey(): String {
        return ThemeHelper.getTheme(this) + ThemeHelper.isUsingSystemColor()
    }

    override fun onApplyUserThemeResource(theme: Theme, isDecorView: Boolean) {
        if (ThemeHelper.isUsingSystemColor()) {
            if (resources.configuration.isNight())
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Dark, true)
            else
                theme.applyStyle(R.style.ThemeOverlay_DynamicColors_Light, true)
        }

        theme.applyStyle(ThemeHelper.getThemeStyleRes(this), true)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
