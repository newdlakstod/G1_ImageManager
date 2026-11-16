package com.imagemanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.imagemanager.app.ui.navigation.AppNavHost
import com.imagemanager.app.ui.theme.ImageManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageManagerTheme {
                Surface {
                    AppNavHost(application as ImageManagerApp)
                }
            }
        }
    }
}
