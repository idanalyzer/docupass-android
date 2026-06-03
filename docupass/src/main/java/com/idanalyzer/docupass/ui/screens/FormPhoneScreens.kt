package com.idanalyzer.docupass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.idanalyzer.docupass.model.CustomFieldType
import com.idanalyzer.docupass.model.DocuPassSession
import com.idanalyzer.docupass.model.PhoneChannel
import com.idanalyzer.docupass.ui.DocuPassViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFormScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val answers = remember { mutableStateMapOf<String, String>() }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("A few more details", style = MaterialTheme.typography.headlineSmall)
        session.customField.forEach { field ->
            val value = answers[field.fieldId] ?: ""
            when (field.parsedType) {
                CustomFieldType.TEXT, CustomFieldType.MULTILINE -> OutlinedTextField(
                    value = value,
                    onValueChange = { answers[field.fieldId] = it },
                    label = { Text(field.fieldLabel) },
                    supportingText = { if (field.fieldDescription.isNotBlank()) Text(field.fieldDescription) },
                    singleLine = field.parsedType == CustomFieldType.TEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
                CustomFieldType.DROPDOWN -> {
                    var open by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                        OutlinedTextField(
                            value = field.dropdownOptions.firstOrNull { it.value == value }?.display ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(field.fieldLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            field.dropdownOptions.forEach { opt ->
                                DropdownMenuItem(text = { Text(opt.display) }, onClick = {
                                    answers[field.fieldId] = opt.value; open = false
                                })
                            }
                        }
                    }
                }
            }
        }
        val allFilled = session.customField.all { (answers[it.fieldId] ?: "").isNotBlank() }
        Button(
            onClick = { vm.submitForm(answers.toMap()) },
            enabled = allFilled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
    }
}

@Composable
fun PhoneScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val preset = session.userPhone.isNotBlank()
    var dialCode by remember { mutableStateOf(session.phoneCountryCode.firstOrNull()?.dialCode ?: "+1") }
    var localNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }

    fun currentNumber(): String? = if (preset) null else dialCode + localNumber.trimStart('0')

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Verify your phone", style = MaterialTheme.typography.headlineSmall)

        if (preset) {
            Text("We'll send a code to ${session.userPhone}", style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dialCode,
                    onValueChange = { dialCode = it },
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth(0.3f),
                )
                OutlinedTextField(
                    value = localNumber,
                    onValueChange = { localNumber = it.filter(Char::isDigit) },
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.sendPhoneCode(currentNumber(), PhoneChannel.SMS); codeSent = true },
                modifier = Modifier.fillMaxWidth(0.5f),
            ) { Text("Send SMS") }
            OutlinedButton(
                onClick = { vm.sendPhoneCode(currentNumber(), PhoneChannel.CALL); codeSent = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Call me") }
        }

        if (codeSent) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                label = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.verifyPhoneCode(currentNumber(), code) },
                enabled = code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verify") }
        }
    }
}
