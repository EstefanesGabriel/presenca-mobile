package com.example.presencamobile.ui.screens.academic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.presencamobile.data.model.*

@Composable
fun AcademicPage(title: String, subtitle: String, vm: AcademicViewModel, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !vm.busy) { Text("Voltar") }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        AcademicFeedback(vm)
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AcademicFeedback(vm: AcademicViewModel) {
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}

@Composable
fun AcademicCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
fun ConfirmAction(title: String, message: String, vm: AcademicViewModel, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(message); AcademicFeedback(vm) } },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !vm.busy) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Cancelar") } }
    )
}

@Composable
fun PermissionSettingsButton() {
    val context = LocalContext.current
    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) {
        Text("Abrir permissões do aplicativo")
    }
}

@Composable
fun rememberLocationAction(onGranted: () -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) onGranted()
        else onError("A chamada precisa de localização precisa. Ative essa permissão nas configurações e tente novamente.")
    }
    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) onGranted()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

fun LessonStatus.label(): String = when (this) {
    LessonStatus.SCHEDULED -> "Agendada"
    LessonStatus.OPEN -> "Chamada aberta"
    LessonStatus.CLOSED -> "Encerrada"
    LessonStatus.CANCELLED -> "Cancelada"
}
fun AttendanceStatus.label(): String = when (this) {
    AttendanceStatus.PRESENT -> "Presente"
    AttendanceStatus.ABSENT -> "Falta"
    AttendanceStatus.PENDING -> "Aguardando encerramento"
}
fun InvitationStatus.label(): String = when (this) {
    InvitationStatus.PENDING -> "Pendente"
    InvitationStatus.ACCEPTED -> "Aceito"
    InvitationStatus.REFUSED -> "Recusado"
    InvitationStatus.CANCELLED -> "Cancelado"
}
