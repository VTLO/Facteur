package com.vtlo.simplesms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vtlo.simplesms.ui.theme.SimpleSmsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimpleSmsTheme {
                Scaffold { padding ->
                    ComposeScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun ComposeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sender = remember { SmsSender(context) }

    var number by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        status = if (granted) "Permission granted." else "SEND_SMS permission denied."
    }

    fun attemptSend() {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }
        status = "Sending…"
        try {
            sender.send(number.trim(), body) { result -> status = result }
        } catch (e: Exception) {
            status = "Error: ${e.message}"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SimpleSMS", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sends ordinary text messages through Android's SmsManager. " +
                "The recipient receives a normal, visible SMS.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text("Recipient number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Phone
            )
        )

        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Message") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "${body.length} characters • " +
                "${if (body.length <= 160) 1 else (body.length + 152) / 153} part(s)",
            style = MaterialTheme.typography.labelMedium
        )

        Button(
            onClick = { attemptSend() },
            enabled = number.isNotBlank() && body.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (hasPermission) "Send SMS" else "Grant permission & send")
        }

        if (status.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    status,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
