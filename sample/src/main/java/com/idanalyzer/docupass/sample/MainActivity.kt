package com.idanalyzer.docupass.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.idanalyzer.docupass.DocuPassConfig
import com.idanalyzer.docupass.DocuPassResult
import com.idanalyzer.docupass.ui.DocuPassView

/**
 * Minimal integration example: paste a DocuPass reference (create one server-side
 * with the ID Analyzer v2 API / SDK) and run the flow.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var reference by remember { mutableStateOf("") }
                var started by remember { mutableStateOf(false) }
                val context = LocalContext.current

                if (started && reference.isNotBlank()) {
                    DocuPassView(
                        config = DocuPassConfig(reference = reference.trim()),
                        modifier = Modifier.fillMaxSize(),
                        onResult = { result ->
                            started = false
                            val msg = when (result) {
                                is DocuPassResult.Completed -> "Completed (${result.code ?: "ok"})"
                                is DocuPassResult.Failed -> "Failed: ${result.message ?: result.code}"
                                is DocuPassResult.Cancelled -> "Cancelled"
                                is DocuPassResult.Error -> "Error: ${result.error.code}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        },
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("DocuPass SDK sample", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            label = { Text("DocuPass reference") },
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Button(onClick = { started = true }, enabled = reference.isNotBlank()) {
                            Text("Start verification")
                        }
                    }
                }
            }
        }
    }
}
