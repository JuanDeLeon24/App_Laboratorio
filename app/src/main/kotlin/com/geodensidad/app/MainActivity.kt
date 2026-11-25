package com.geodensidad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.geodensidad.app.ui.AppNavHost
import com.geodensidad.app.ui.theme.GeoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GeoTheme { AppNavHost() } }
    }
}
