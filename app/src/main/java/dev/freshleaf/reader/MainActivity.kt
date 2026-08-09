package dev.freshleaf.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.freshleaf.reader.data.ThemeMode
import dev.freshleaf.reader.ui.FreshLeafScreen
import dev.freshleaf.reader.ui.FreshLeafViewModel
import dev.freshleaf.reader.ui.FreshLeafViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: FreshLeafViewModel by viewModels {
        (application as FreshLeafApplication).let { FreshLeafViewModelFactory(it.repository, it.userPreferences) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences by viewModel.preferences.collectAsState()
            val dark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Surface { FreshLeafScreen(viewModel) }
            }
        }
    }
}
