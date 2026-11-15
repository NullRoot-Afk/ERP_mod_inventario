package com.example.erprukainventario.core.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cámara (CameraX) + lector ML Kit empaquetados para reutilizar en cualquier
 * pantalla de escaneo. Maneja el permiso, la pausa mientras hay un diálogo
 * abierto, evita lecturas repetidas del mismo código y libera todo al destruirse
 * la Activity (se registra como observador de su ciclo de vida).
 *
 * Debe construirse en onCreate (registra un ActivityResultLauncher).
 */
public class BarcodeScannerController implements DefaultLifecycleObserver {

    public interface Listener {
        /** Siempre en el hilo principal. El escaneo queda en pausa hasta llamar a resume(). */
        void onBarcode(String value);
    }

    private static final String TAG = "BarcodeScanner";
    private static final long SAME_CODE_COOLDOWN_MS = 2000;

    private final AppCompatActivity activity;
    private final PreviewView previewView;
    private final Listener listener;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final BarcodeScanner barcodeScanner;
    private final ActivityResultLauncher<String> permissionLauncher;

    private volatile boolean paused = false;
    private boolean cameraStarted = false;
    private String lastValue;
    private long lastValueAt;

    public BarcodeScannerController(AppCompatActivity activity, PreviewView previewView, Listener listener) {
        this.activity = activity;
        this.previewView = previewView;
        this.listener = listener;

        // Formatos de la guía §2: EAN-13, UPC, Code128 y QR (más EAN-8 por ser común en retail).
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
                .build();
        this.barcodeScanner = BarcodeScanning.getClient(options);

        this.permissionLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) startCamera();
                    else showPermissionDeniedDialog();
                });

        activity.getLifecycle().addObserver(this);
    }

    /** Pide permiso si hace falta y enciende la cámara. */
    public void start() {
        if (hasPermission()) startCamera();
        else permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        // Cubre volver desde Ajustes tras conceder el permiso a mano.
        if (!cameraStarted && hasPermission()) startCamera();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    @SuppressWarnings("UnsafeOptInUsageError")
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (paused || mediaImage == null) {
                        imageProxy.close();
                        return;
                    }
                    InputImage image = InputImage.fromMediaImage(mediaImage,
                            imageProxy.getImageInfo().getRotationDegrees());
                    barcodeScanner.process(image)
                            .addOnSuccessListener(barcodes -> {
                                for (Barcode barcode : barcodes) {
                                    String raw = barcode.getRawValue();
                                    if (raw != null && !raw.isEmpty()) {
                                        deliver(raw.trim());
                                        break;
                                    }
                                }
                            })
                            .addOnCompleteListener(task -> imageProxy.close());
                });

                provider.unbindAll();
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                cameraStarted = true;
            } catch (Exception e) {
                // Incluye equipos/emuladores sin cámara trasera: queda disponible el ingreso manual.
                Log.e(TAG, "No se pudo iniciar la cámara", e);
                Toast.makeText(activity, "Cámara no disponible: usa el ingreso manual.", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    /** Los listeners de ML Kit llegan en el hilo principal. */
    private void deliver(String value) {
        if (paused) return;
        long now = SystemClock.elapsedRealtime();
        if (value.equals(lastValue) && now - lastValueAt < SAME_CODE_COOLDOWN_MS) return;
        lastValue = value;
        lastValueAt = now;
        paused = true;
        listener.onBarcode(value);
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("Permiso de cámara")
                .setMessage("Para escanear códigos necesitas permitir el acceso a la cámara. "
                        + "Puedes habilitarlo en Ajustes, o ingresar los códigos manualmente.")
                .setPositiveButton("Abrir ajustes", (d, w) -> activity.startActivity(
                        new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", activity.getPackageName(), null))))
                .setNegativeButton("Ahora no", null)
                .show();
    }
}
