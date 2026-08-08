package dev.freshleaf.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.freshleaf.reader.ui.FreshLeafScreen
import dev.freshleaf.reader.ui.FreshLeafViewModel
import dev.freshleaf.reader.ui.FreshLeafViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: FreshLeafViewModel by viewModels {
        FreshLeafViewModelFactory((application as FreshLeafApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { FreshLeafScreen(viewModel) }
            }
        }
    }
}

