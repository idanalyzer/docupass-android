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
import com.idanalyzer.docupass.ui.LocalDocuPassStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFormScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val s = LocalDocuPassStrings.current
    val answers = remember { mutableStateMapOf<String, String>() }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(s.customFormTitle, style = MaterialTheme.typography.headlineSmall)
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
        ) { Text(s.continueButton) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(vm: DocuPassViewModel, session: DocuPassSession) {
    val s = LocalDocuPassStrings.current
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
        Text(s.phoneTitle, style = MaterialTheme.typography.headlineSmall)

        if (preset) {
            Text("${s.phonePresetPrefix}${session.userPhone}", style = MaterialTheme.typography.bodyLarge)
        } else {
            var ccOpen by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.phoneCountryCode.isNotEmpty()) {
                    // Dial-code picker populated from the session (matches the web's <select>).
                    ExposedDropdownMenuBox(
                        expanded = ccOpen,
                        onExpandedChange = { ccOpen = it },
                        modifier = Modifier.fillMaxWidth(0.42f),
                    ) {
                        OutlinedTextField(
                            value = dialCode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(s.phoneCodeLabel) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ccOpen) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = ccOpen, onDismissRequest = { ccOpen = false }) {
                            session.phoneCountryCode.forEach { pc ->
                                DropdownMenuItem(
                                    text = { Text("${pc.name} ${pc.dialCode}") },
                                    onClick = { dialCode = pc.dialCode; ccOpen = false },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = dialCode,
                        onValueChange = { dialCode = it },
                        label = { Text(s.phoneCodeLabel) },
                        modifier = Modifier.fillMaxWidth(0.42f),
                    )
                }
                OutlinedTextField(
                    value = localNumber,
                    onValueChange = { localNumber = it.filter(Char::isDigit) },
                    label = { Text(s.phoneNumberLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.sendPhoneCode(currentNumber(), PhoneChannel.SMS); codeSent = true },
                modifier = Modifier.fillMaxWidth(0.5f),
            ) { Text(s.phoneSendSms) }
            OutlinedButton(
                onClick = { vm.sendPhoneCode(currentNumber(), PhoneChannel.CALL); codeSent = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.phoneCall) }
        }

        if (codeSent) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                label = { Text(s.phoneCodeEntryLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { vm.verifyPhoneCode(currentNumber(), code) },
                enabled = code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(s.phoneVerify) }
        }
    }
}
