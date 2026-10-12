package com.example.presencamobile.ui.screens.academic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.model.*

@Composable
fun InboxRoute(profile: UserProfile, onBack: () -> Unit) {
    val vm: InboxViewModel = viewModel(key = "inbox-${profile.id}", factory = viewModelFactory { initializer { InboxViewModel(profile) } })
    DisposableEffect(vm) { vm.refresh(); onDispose { vm.cancelWork() } }
    BackHandler { if (!vm.busy) onBack() }
    AcademicPage("Notificações e convites", profile.name, vm, onBack) {
        OutlinedButton(onClick = vm::refresh, enabled = !vm.busy) { Text("Atualizar") }
        Text("As novidades são carregadas ao abrir esta tela ou tocar em Atualizar.")
        if (profile.role == UserRole.STUDENT) {
            Text("Convites para turmas", style = MaterialTheme.typography.titleLarge)
            if (vm.loaded && vm.invitations.isEmpty()) Text("Você ainda não recebeu convites.")
            vm.invitations.forEach { invitation -> AcademicCard {
                Text(invitation.classroomName, style = MaterialTheme.typography.titleMedium)
                Text("Professor: ${invitation.teacherName}")
                Text("${invitation.status.label()} • ${AcademicDates.display(invitation.createdAt)}")
                if (invitation.status == InvitationStatus.PENDING) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.respond(invitation.id, true) }, enabled = !vm.busy) { Text("Aceitar") }
                    OutlinedButton(onClick = { vm.respond(invitation.id, false) }, enabled = !vm.busy) { Text("Recusar") }
                }
            } }
        }
        Text("Notificações", style = MaterialTheme.typography.titleLarge)
        if (vm.loaded && vm.notifications.isEmpty()) Text("Nenhuma notificação.")
        vm.notifications.forEach { notification -> AcademicCard {
            Text(if (notification.readAt == null) "Nova" else "Lida", style = MaterialTheme.typography.labelLarge)
            Text(notification.message); Text(AcademicDates.display(notification.createdAt))
            if (notification.readAt == null) TextButton(onClick = { vm.markRead(notification.id) }, enabled = !vm.busy) { Text("Marcar como lida") }
        } }
    }
}
