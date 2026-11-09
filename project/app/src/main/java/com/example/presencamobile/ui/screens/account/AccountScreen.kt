package com.example.presencamobile.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.model.UserRole

@Composable
fun AccountRoute(profile: UserProfile, email: String?, onProfileUpdated: (UserProfile) -> Unit, onBack: () -> Unit) {
    val vm: AccountViewModel = viewModel(key = "account-${profile.id}", factory = viewModelFactory {
        initializer { AccountViewModel(profile, email) }
    })
    DisposableEffect(vm) {
        vm.refresh(onProfileUpdated)
        onDispose { vm.cancelWork() }
    }
    BackHandler { if (!vm.uiState.isBusy) onBack() }
    val state = vm.uiState
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !state.isBusy) { Text("Voltar") }
        Text("Minha conta", style = MaterialTheme.typography.headlineMedium)
        Text(if (profile.role == UserRole.TEACHER) "Perfil: Professor" else "Perfil: Aluno")
        Text("E-mail atual: ${state.email.ifBlank { "Não disponível" }}")
        OutlinedButton(onClick = { vm.refresh(onProfileUpdated) }, enabled = !state.isBusy) { Text("Atualizar dados") }
        if (state.isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        AccountCard("Dados pessoais") {
            AccountField("Nome", state.name, vm::onNameChange, !state.isBusy)
            Button(onClick = { vm.saveName(onProfileUpdated) }, enabled = !state.isBusy) { Text("Salvar nome") }
        }
        AccountCard("Alterar senha") {
            Text("Esta é a senha de login. Use pelo menos 8 caracteres.")
            AccountField("Nova senha", state.newPassword, vm::onNewPasswordChange, !state.isBusy, KeyboardType.Password)
            AccountField("Confirmar nova senha", state.confirmPassword, vm::onConfirmPasswordChange, !state.isBusy, KeyboardType.Password)
            Button(onClick = vm::savePassword, enabled = !state.isBusy) { Text("Alterar senha") }
        }
    }
}

@Composable
private fun AccountCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun AccountField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, enabled = enabled,
        singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None)
}
