package com.example.presencamobile.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    sessionMessage: String? = null
) {
    val email = uiState.email
    val password = uiState.password
    val emailError = uiState.emailError
    val passwordError = uiState.passwordError
    val formMessage = uiState.formMessage ?: sessionMessage

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Presença Mobile",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Entre na sua conta",
            style = MaterialTheme.typography.bodyLarge
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            enabled = !uiState.isLoading,
            label = { Text("E-mail") },
            singleLine = true,
            isError = emailError != null,
            supportingText = {
                if (emailError != null) {
                    Text(text = emailError.orEmpty())
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = !uiState.isLoading,
            label = { Text("Senha") },
            singleLine = true,
            isError = passwordError != null,
            supportingText = {
                if (passwordError != null) {
                    Text(text = passwordError.orEmpty())
                }
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onLoginClick,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isLoading) "Entrando..." else "Entrar")
        }

        if (formMessage != null){
            Text (
                text = formMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        TextButton(
            onClick = onNavigateToRegister,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Não tenho conta. Cadastrar")
        }
    }
}
