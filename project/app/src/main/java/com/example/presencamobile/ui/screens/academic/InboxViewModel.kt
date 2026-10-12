package com.example.presencamobile.ui.screens.academic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.presencamobile.data.model.*
import com.example.presencamobile.data.repository.AcademicRepository

class InboxViewModel(private val profile: UserProfile, private val repository: AcademicRepository = AcademicRepository()) : AcademicViewModel() {
    var notifications by mutableStateOf<List<AppNotification>>(emptyList()); private set
    var invitations by mutableStateOf<List<ClassroomInvitation>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    fun refresh() { perform { reload() } }
    private suspend fun reload() {
        val incoming = repository.notifications()
        val invites = if (profile.role == UserRole.STUDENT) repository.invitations() else emptyList()
        notifications = incoming; invitations = invites; loaded = true
    }
    fun respond(id: String, accept: Boolean) {
        if (profile.role != UserRole.STUDENT || invitations.none { it.id == id && it.status == InvitationStatus.PENDING }) return
        perform { repository.respondInvitation(id, accept); reload(); notice = if (accept) "Convite aceito. A turma aparecerá na sua lista." else "Convite recusado." }
    }
    fun markRead(id: String) {
        if (notifications.none { it.id == id && it.readAt == null }) return
        perform { repository.markRead(id); reload() }
    }
}
