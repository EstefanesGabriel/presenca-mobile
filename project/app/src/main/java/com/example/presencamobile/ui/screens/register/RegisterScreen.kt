package com.example.presencamobile.ui.screens.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.presencamobile.data.model.UserRole

@Composable
fun RegisterScreen(
    uiState: RegisterUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRoleChange: (UserRole) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Crie sua conta",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            label = { Text("Nome") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Senha") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password
            ),
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
        )

        Text(
            text = "Quero me cadastrar como:",
            style = MaterialTheme.typography.titleMedium
        )

        Column(
            modifier = Modifier.selectableGroup()
        ) {
            UserRole.entries.forEach { role ->
                val selected = uiState.role == role

                val label = when (role) {
                    UserRole.STUDENT -> "Aluno"
                    UserRole.TEACHER -> "Professor"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            enabled = !uiState.isLoading,
                            onClick = { onRoleChange(role) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = !uiState.isLoading
                    )

                    Text(text = label)
                }
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }

        uiState.successMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = onRegisterClick,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (uiState.isLoading) {
                    "Cadastrando..."
                } else {
                    "Cadastrar"
                }
            )
        }

        TextButton(
            onClick = onNavigateToLogin,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Já tenho conta. Entrar")
        }
    }
}
