package com.example.presencamobile.ui.screens.teacher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.ui.auth.SessionUiState
import com.example.presencamobile.ui.screens.academic.TeacherClassroomRoute

@Composable
fun TeacherRoute(profile: UserProfile, session: SessionUiState, onSignOut: () -> Unit) {
    val teacher: TeacherViewModel = viewModel(
        key = "teacher-${profile.id}",
        factory = viewModelFactory { initializer { TeacherViewModel(profile) } }
    )
    val state = teacher.uiState
    DisposableEffect(teacher) { teacher.refresh(); onDispose { } }
    BackHandler(enabled = state.isCreating) { teacher.cancelCreate() }
    BackHandler(enabled = state.selectedClassroom != null) { teacher.closeParticipants() }

    val selectedClassroom = state.selectedClassroom
    if (selectedClassroom != null) {
        TeacherClassroomRoute(profile, selectedClassroom) { teacher.closeParticipants(); teacher.refresh() }
    } else if (state.isCreating) {
        CreateClassroomScreen(
            state = state,
            onNameChange = teacher::onNameChange,
            onSubjectChange = teacher::onSubjectChange,
            onSemesterChange = teacher::onSemesterChange,
            onPasswordChange = teacher::onPasswordChange,
            onSave = teacher::createClassroom,
            onCancel = teacher::cancelCreate
        )
    } else {
        TeacherScreen(profile.name, state, session, teacher::openCreate, teacher::refresh, onSignOut, teacher::openParticipants)
    }
}

@Composable
fun TeacherScreen(
    teacherName: String,
    state: TeacherUiState,
    session: SessionUiState,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onParticipants: (Classroom) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Área do professor", style = MaterialTheme.typography.labelLarge)
                Text("Olá, $teacherName", style = MaterialTheme.typography.headlineSmall)
                Text("Minhas turmas", style = MaterialTheme.typography.titleLarge)
                Text("Organize suas turmas por disciplina e período.")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreate, enabled = !state.isLoading && !session.isSigningOut, modifier = Modifier.weight(1f)) {
                    Text("Criar turma")
                }
                OutlinedButton(onClick = onRefresh, enabled = !state.isLoading && !session.isSigningOut) {
                    Text("Atualizar")
                }
            }
        }
        if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.successMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        state.listError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Tentar novamente") }
            }
        }
        if (!state.isLoading && state.listError == null && state.classrooms.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sua primeira turma começa aqui", style = MaterialTheme.typography.titleMedium)
                        Text("Toque em Criar turma para informar a disciplina e o período.")
                    }
                }
            }
        }
        items(state.classrooms, key = { it.id }) { classroom ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(classroom.name, style = MaterialTheme.typography.titleLarge)
                    Text(classroom.subject)
                    Text("Período: ${classroom.semester}", style = MaterialTheme.typography.bodyMedium)
                    SelectionContainer { Text("Código: ${classroom.accessCode}", style = MaterialTheme.typography.labelLarge) }
                    if (!classroom.isActive) Text("Turma encerrada", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { onParticipants(classroom) }, enabled = !session.isSigningOut) {
                        Text("Abrir turma")
                    }
                }
            }
        }
        item {
            session.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onSignOut, enabled = !session.isSigningOut, modifier = Modifier.padding(bottom = 24.dp)) {
                Text(if (session.isSigningOut) "Saindo..." else "Sair da conta")
            }
        }
    }
}
