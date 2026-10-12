package com.example.presencamobile.ui.screens.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun JoinClassroomScreen(state: StudentUiState, onCode: (String) -> Unit, onPassword: (String) -> Unit, onJoin: () -> Unit, onBack: () -> Unit) {
    val form = state.form
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack, enabled = !state.isSaving) { Text("Voltar para minhas turmas") }
        Text("Entrar em turma", style = MaterialTheme.typography.headlineMedium)
        Text("Informe o código e a senha disponibilizados pelo professor.")
        OutlinedTextField(
            value = form.code,
            onValueChange = onCode,
            label = { Text("Código da turma") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            supportingText = { Text(form.codeError ?: "O código tem 12 caracteres.") },
            isError = form.codeError != null,
            enabled = !state.isSaving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.password,
            onValueChange = onPassword,
            label = { Text("Senha da turma") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(form.passwordError ?: "Use a senha da turma, não a da sua conta.") },
            isError = form.passwordError != null,
            enabled = !state.isSaving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        state.joinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onJoin, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isSaving) "Entrando..." else "Entrar na turma")
        }
    }
}
