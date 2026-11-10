package dev.sukisu.themeforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sukisu.themeforge.ui.EditorScreen
import dev.sukisu.themeforge.ui.HomeScreen
import dev.sukisu.themeforge.ui.ThemeForgeTheme
import dev.sukisu.themeforge.vm.EditorViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThemeForgeTheme {
                val vm: EditorViewModel = viewModel()
                val doc = vm.doc
                if (doc == null) {
                    HomeScreen(vm)
                } else {
                    EditorScreen(vm, doc)
                }
            }
        }
    }
}
