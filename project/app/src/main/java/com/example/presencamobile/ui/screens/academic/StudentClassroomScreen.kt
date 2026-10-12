package com.example.presencamobile.ui.screens.academic

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.presencamobile.data.location.LocationProvider
import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.R
import com.example.presencamobile.ui.scanner.QrScannerActivity
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun StudentClassroomRoute(profile: UserProfile, classroom: Classroom, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: StudentClassroomViewModel = viewModel(key = "student-class-${profile.id}-${classroom.id}", factory = viewModelFactory {
        initializer { StudentClassroomViewModel(profile, classroom, LocationProvider(context)) }
    })
    DisposableEffect(vm) { vm.refresh(); onDispose { vm.cancelWork() } }
    BackHandler { if (!vm.busy) onBack() }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { content ->
            if (result.formatName == BarcodeFormat.QR_CODE.name) vm.scan(content)
            else vm.showError("Leia o QR Code de presença mostrado pelo professor.")
        }
    }
    val prompt = stringResource(R.string.qr_scanner_prompt)
    val launchScanner = {
        scanner.launch(ScanOptions()
            .setCaptureActivity(QrScannerActivity::class.java)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setOrientationLocked(false)
            .setBeepEnabled(false)
            .setPrompt(prompt))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val camera = result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (camera && fine) launchScanner()
        else vm.showError("Permita câmera e localização precisa para ler o QR e registrar presença. Você pode ajustar as permissões abaixo.")
    }
    val current = vm.classroom
    AcademicPage(current.name, "${current.subject} • ${current.semester}", vm, onBack) {
        if (current.isActive) {
            Text("Leia o QR Code da aula no local indicado pelo professor. Sua localização será conferida pelo banco.")
            Button(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) launchScanner()
                else permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, enabled = !vm.busy && vm.loaded) { Text("Ler QR Code e registrar presença") }
            PermissionSettingsButton()
        } else Text("Turma encerrada — histórico disponível.")
        vm.receipt?.let { receipt -> AcademicCard {
            Text(if (receipt.alreadyRecorded) "Presença já registrada" else "Presença confirmada", style = MaterialTheme.typography.titleMedium)
            Text("Você pode atualizar o histórico para visualizar o registro.")
        } }
        OutlinedButton(onClick = vm::refresh, enabled = !vm.busy) { Text("Atualizar histórico") }
        Text("Minha frequência", style = MaterialTheme.typography.titleLarge)
        if (vm.loaded) AcademicCard {
            val summary = vm.summary
            Text(summary.percentage?.let { "$it% de presença" } ?: "Nenhuma aula encerrada ainda", style = MaterialTheme.typography.titleMedium)
            Text("${summary.present} presença(s) • ${summary.absent} falta(s) • ${summary.total} aula(s) encerrada(s)")
            Text("Aulas abertas ou agendadas não entram no percentual.")
        }
        if (vm.loaded && vm.history.isEmpty()) Text("Nenhuma aula disponível no seu histórico.")
        vm.history.forEach { item -> AcademicCard {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(AcademicDates.display(item.startsAt))
            Text("${item.lessonStatus.label()} • ${item.attendanceStatus.label()}")
            item.recordedAt?.let { Text("Registro: ${AcademicDates.display(it)}") }
        } }
    }
}
