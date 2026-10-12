package com.example.presencamobile.ui.screens.teacher

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CreateClassroomScreen(
    state: TeacherUiState,
    onNameChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onSemesterChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onCancel, enabled = !state.isSaving) { Text("Voltar para minhas turmas") }
        Text("Criar turma", style = MaterialTheme.typography.headlineMedium)
        Text("Dê um nome à turma e defina as informações de acesso.")
        ClassroomField("Nome da turma", "Ex.: Engenharia de Software — A", form.name, form.nameError, !state.isSaving, onNameChange)
        ClassroomField("Disciplina", "Ex.: Desenvolvimento Mobile", form.subject, form.subjectError, !state.isSaving, onSubjectChange)
        ClassroomField("Período", "Ex.: 2026/2", form.semester, form.semesterError, !state.isSaving, onSemesterChange)
        OutlinedTextField(
            value = form.accessPassword,
            onValueChange = onPasswordChange,
            label = { Text("Senha de acesso à turma") },
            supportingText = { Text(form.passwordError ?: "Use pelo menos 6 caracteres. Esta senha é diferente da senha da sua conta.") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = form.passwordError != null,
            enabled = !state.isSaving,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text("O código será gerado ao salvar. Guarde a senha: ela não será exibida novamente.", style = MaterialTheme.typography.bodySmall)
        state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isSaving) "Salvando..." else "Salvar turma")
        }
    }
}

@Composable
private fun ClassroomField(label: String, example: String, value: String, error: String?, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(example) },
        supportingText = { error?.let { Text(it) } },
        isError = error != null,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
