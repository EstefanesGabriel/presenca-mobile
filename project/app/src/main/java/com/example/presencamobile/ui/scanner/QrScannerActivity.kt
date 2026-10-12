package com.example.presencamobile.ui.scanner

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.presencamobile.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** Usa o ciclo de vida da câmera e o retorno do ScanContract com uma tela própria para QR. */
class QrScannerActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_qr_scanner)
        val root = findViewById<View>(R.id.qr_scanner_root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<View>(R.id.qr_scanner_back).setOnClickListener { finish() }

        val scanner = findViewById<DecoratedBarcodeView>(R.id.qr_scanner)
        scanner.viewFinder.setLaserVisibility(false)
        return scanner
    }
}
