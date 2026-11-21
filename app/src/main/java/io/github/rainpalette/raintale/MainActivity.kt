package io.github.rainpalette.raintale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rainpalette.raintale.crash.CrashReporter
import io.github.rainpalette.raintale.ui.AppRoot
import io.github.rainpalette.raintale.ui.theme.FirstVRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程早期装全局崩溃捕获：写本地报告，下次启动提示查看/分享（见 CrashReporter / AppRoot）。
        CrashReporter.install(this)
        enableEdgeToEdge()
        setContent {
            FirstVRTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
