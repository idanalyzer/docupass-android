package com.idanalyzer.docupass.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idanalyzer.docupass.KYCScreen

/**
 * Minimal integration example: paste a DocuPass reference (create one server-side
 * with the ID Analyzer v2 API / SDK) and run the flow.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FFAB),
                    background = Color(0xFF050A08),
                    surface = Color(0xFF0D1713),
                    onPrimary = Color(0xFF052017),
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                var reference by remember { mutableStateOf("") }
                var started by remember { mutableStateOf(false) }

                if (started && reference.isNotBlank()) {
                    KYCScreen(
                        reference = reference.trim(),
                        onFinish = { started = false },
                        onBackAtFirstStep = { started = false },
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "DocuPass KYC",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Enter a DocuPass reference to start the native verification flow.",
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Spacer(Modifier.height(28.dp))
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it },
                            label = { Text("DocuPass reference") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FFAB),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.38f),
                                focusedLabelColor = Color(0xFF00FFAB),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.65f),
                                cursorColor = Color(0xFF00FFAB),
                            ),
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { started = true },
                            enabled = reference.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFAB)),
                        ) {
                            Text("Start KYC", color = Color(0xFF052017), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
