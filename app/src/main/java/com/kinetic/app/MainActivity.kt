package com.kinetic.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kinetic.app.data.AppSettings
import com.kinetic.app.model.EditorState
import com.kinetic.app.ui.EditorScreen
import com.kinetic.app.ui.HomeScreen
import com.kinetic.app.ui.KineticTheme
import com.kinetic.app.ui.SettingsScreen

private enum class Screen { HOME, EDITOR, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val settings = AppSettings(applicationContext)
        setContent {
            KineticTheme {
                var screen by remember { mutableStateOf(Screen.HOME) }
                var settingsFrom by remember { mutableStateOf(Screen.HOME) }
                var editor by remember { mutableStateOf<EditorState?>(null) }

                when (screen) {
                    Screen.HOME -> HomeScreen(
                        // every new composition starts completely empty
                        onNewComposition = { editor = EditorState(settings); screen = Screen.EDITOR },
                        onImportProject = { },
                        onImportPlugin = { },
                        onOpenSettings = { settingsFrom = Screen.HOME; screen = Screen.SETTINGS }
                    )
                    Screen.EDITOR -> {
                        val e = editor
                        if (e != null) {
                            EditorScreen(
                                editor = e,
                                onExit = { e.playing = false; editor = null; screen = Screen.HOME },
                                onOpenSettings = { settingsFrom = Screen.EDITOR; screen = Screen.SETTINGS }
                            )
                        }
                    }
                    Screen.SETTINGS -> {
                        BackHandler { screen = settingsFrom }
                        SettingsScreen(settings) { screen = settingsFrom }
                    }
                }
            }
        }
    }
}
