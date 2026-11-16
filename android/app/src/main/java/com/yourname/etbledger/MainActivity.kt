package com.yourname.etbledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.yourname.etbledger.ui.navigation.EtbLedgerAppShell
import com.yourname.etbledger.ui.theme.EtbLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EtbLedgerTheme {
                EtbLedgerAppShell()
            }
        }
    }
}
