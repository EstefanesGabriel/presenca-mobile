package com.example.presencamobile.ui.screens.student

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.ui.screens.academic.StudentClassroomRoute
import com.example.presencamobile.ui.auth.SessionUiState

@Composable
fun StudentRoute(profile: UserProfile, session: SessionUiState, onSignOut: () -> Unit) {
    val student: StudentViewModel = viewModel(
        key = "student-${profile.id}",
        factory = viewModelFactory { initializer { StudentViewModel(profile) } }
    )
    val state = student.uiState
    DisposableEffect(student) { student.refresh(); onDispose { } }
    var selectedId by rememberSaveable(profile.id) { mutableStateOf<String?>(null) }
    val selected = state.classrooms.find { it.id == selectedId }
    BackHandler(enabled = state.isJoining) { student.cancelJoin() }
    if (selected != null) {
        StudentClassroomRoute(profile, selected) { selectedId = null; student.refresh() }
    } else if (state.isJoining) {
        JoinClassroomScreen(state, student::onCodeChange, student::onPasswordChange, student::joinClassroom, student::cancelJoin)
    } else {
        StudentScreen(profile.name, state, session, student::openJoin, student::refresh, onSignOut) { selectedId = it.id }
    }
}

@Composable
fun StudentScreen(
    studentName: String,
    state: StudentUiState,
    session: SessionUiState,
    onJoin: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onClassroom: (Classroom) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Área do aluno", style = MaterialTheme.typography.labelLarge)
                Text("Olá, $studentName", style = MaterialTheme.typography.headlineSmall)
                Text("Minhas turmas", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onJoin, enabled = !state.isLoading && !session.isSigningOut, modifier = Modifier.weight(1f)) { Text("Entrar em turma") }
                OutlinedButton(onClick = onRefresh, enabled = !state.isLoading && !session.isSigningOut) { Text("Atualizar") }
            }
        }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.successMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        state.listError?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh, enabled = !state.isLoading && !session.isSigningOut) { Text("Tentar novamente") }
            }
        }
        if (!state.isLoading && state.listError == null && state.classrooms.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Você ainda não participa de turmas", style = MaterialTheme.typography.titleMedium)
                        Text("Peça o código e a senha ao professor e toque em Entrar em turma.")
                    }
                }
            }
        }
        items(state.classrooms, key = { it.id }) { classroom ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(classroom.name, style = MaterialTheme.typography.titleLarge)
                    Text(classroom.subject)
                    Text("Período: ${classroom.semester}")
                    if (!classroom.isActive) Text("Turma encerrada", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { onClassroom(classroom) }, enabled = !session.isSigningOut) { Text("Presença e histórico") }
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
