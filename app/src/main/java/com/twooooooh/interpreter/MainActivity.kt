package com.twooooooh.interpreter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.twooooooh.interpreter.ui.MainScreen
import com.twooooooh.interpreter.ui.theme.InterpreterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InterpreterTheme {
                MainScreen()
            }
        }
    }
}
