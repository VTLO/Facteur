package com.vtlo.facteur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtlo.facteur.model.HTTP_METHODS
import com.vtlo.facteur.model.HeaderEntry
import com.vtlo.facteur.model.HttpResult
import com.vtlo.facteur.model.METHODS_WITHOUT_BODY
import com.vtlo.facteur.network.FacteurRequestException
import com.vtlo.facteur.network.sendHttpRequest
import com.vtlo.facteur.ui.theme.FacteurTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FacteurTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FacteurApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacteurApp() {
    var method by rememberSaveable { mutableStateOf(HTTP_METHODS[1]) } // POST
    var url by rememberSaveable { mutableStateOf("https://") }
    var payload by rememberSaveable { mutableStateOf("") }
    var nextHeaderId by remember { mutableStateOf(1L) }
    var headers by remember {
        mutableStateOf(listOf(HeaderEntry(id = 0L, key = "Content-Type", value = "application/json")))
    }

    var isSending by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<HttpResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val bodySupported = method !in METHODS_WITHOUT_BODY

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Facteur", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Method + URL
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                var methodExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = methodExpanded,
                    onExpandedChange = { methodExpanded = it },
                    modifier = Modifier.weight(0.35f)
                ) {
                    OutlinedTextField(
                        value = method,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = methodExpanded,
                        onDismissRequest = { methodExpanded = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        HTTP_METHODS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    method = option
                                    methodExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.weight(0.65f)
                )
            }

            // Headers
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Headers", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            headers = headers + HeaderEntry(id = nextHeaderId)
                            nextHeaderId += 1
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Add header")
                        }
                    }

                    headers.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = entry.key,
                                onValueChange = { newKey ->
                                    headers = headers.map { if (it.id == entry.id) it.copy(key = newKey) else it }
                                },
                                label = { Text("Key") },
                                singleLine = true,
                                modifier = Modifier.weight(0.45f)
                            )
                            OutlinedTextField(
                                value = entry.value,
                                onValueChange = { newValue ->
                                    headers = headers.map { if (it.id == entry.id) it.copy(value = newValue) else it }
                                },
                                label = { Text("Value") },
                                singleLine = true,
                                modifier = Modifier.weight(0.45f)
                            )
                            IconButton(onClick = { headers = headers.filterNot { it.id == entry.id } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove header")
                            }
                        }
                    }
                }
            }

            // Body / payload
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text(if (bodySupported) "Body / payload" else "Body not sent for $method") },
                enabled = bodySupported,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )

            Button(
                onClick = {
                    errorMessage = null
                    result = null
                    isSending = true
                    scope.launch {
                        try {
                            val headerPairs = headers
                                .filter { it.key.isNotBlank() }
                                .map { it.key to it.value }
                            val response = withContext(Dispatchers.IO) {
                                sendHttpRequest(method, url, headerPairs, payload)
                            }
                            result = response
                        } catch (e: FacteurRequestException) {
                            errorMessage = e.message
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Request failed"
                        } finally {
                            isSending = false
                        }
                    }
                },
                enabled = !isSending && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (isSending) "Sending..." else "Send")
            }

            errorMessage?.let { message ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            result?.let { response -> ResponseView(response) }
        }
    }
}

@Composable
private fun ResponseView(result: HttpResult) {
    val statusColor = when (result.statusCode) {
        in 200..299 -> MaterialTheme.colorScheme.primary
        in 300..399 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    Card {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${result.statusCode} ${result.statusMessage}",
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "· ${result.protocol} · ${result.elapsedMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text("Response headers", fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    text = if (result.headers.isEmpty()) {
                        "(none)"
                    } else {
                        result.headers.joinToString("\n") { (k, v) -> "$k: $v" }
                    },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Response body", fontWeight = FontWeight.SemiBold)
                if (result.bodyTruncated) {
                    Text(
                        "(truncated)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = result.body.ifEmpty { "(empty body)" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
