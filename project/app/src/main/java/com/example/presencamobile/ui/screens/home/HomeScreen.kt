package com.example.presencamobile.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.presencamobile.ui.auth.SessionUiState
import com.example.presencamobile.data.model.UserRole
import com.example.presencamobile.ui.screens.teacher.TeacherRoute
import com.example.presencamobile.ui.screens.student.StudentRoute
import com.example.presencamobile.ui.screens.academic.InboxRoute

@Composable
fun HomeScreen(
    uiState: SessionUiState,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryProfile: () -> Unit = {}
) {
    val profile = uiState.profile
    if (profile != null) {
        var showInbox by rememberSaveable(profile.id) { mutableStateOf(false) }
        if (showInbox) {
            InboxRoute(profile) { showInbox = false }
        } else {
            Column(modifier.fillMaxSize()) {
                TextButton(onClick = { showInbox = true }, enabled = !uiState.isSigningOut, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Notificações e convites")
                }
                Box(Modifier.weight(1f)) {
                    if (profile.role == UserRole.TEACHER) TeacherRoute(profile, uiState, onSignOutClick)
                    else StudentRoute(profile, uiState, onSignOutClick)
                }
            }
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Presença Mobile", style = MaterialTheme.typography.headlineMedium)
        when {
            uiState.isProfileLoading -> {
                CircularProgressIndicator()
                Text("Carregando seu perfil...")
            }
            else -> {
                Text(uiState.profileError ?: "Seu perfil ainda não está disponível.", color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetryProfile, enabled = !uiState.isSigningOut) { Text("Tentar novamente") }
            }
        }
        uiState.email?.let { Text(it) }
        uiState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onSignOutClick,
            enabled = !uiState.isSigningOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isSigningOut) "Saindo..." else "Sair da conta")
        }
    }
}
