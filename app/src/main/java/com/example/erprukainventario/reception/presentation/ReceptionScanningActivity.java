package com.example.erprukainventario.reception.presentation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityReceptionScanningBinding;
import com.example.erprukainventario.databinding.DialogQuantityInputBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReceptionScanningActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";
    private static final String TAG = "ReceptionScan";

    private ActivityReceptionScanningBinding binding;
    private ReceptionScanningViewModel viewModel;
    private ReceptionScanningAdapter adapter;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private boolean isScanningPaused = false;
    private boolean cameraStarted = false;

    // Reemplaza requestPermissions/onRequestPermissionsResult.
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showPermissionDeniedDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.util.Log.d("ReceptionScan", "ReceptionScanningActivity ABIERTA");
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionScanningBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionScanningViewModel.class);
        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient();

        setupList();
        setupListeners();
        observeUiState();

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        viewModel.loadOrderDetail(orderId);

        checkCameraPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cubre el caso de volver desde Ajustes tras conceder el permiso a mano.
        if (!cameraStarted && hasCameraPermission()) {
            startCamera();
        }
    }

    private void setupList() {
        adapter = new ReceptionScanningAdapter(this::showQuantityDialog);
        binding.rvProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProducts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.tabAll.setOnClickListener(v -> viewModel.setTab(ReceptionScanningUiState.FilterTab.ALL));
        binding.tabScanned.setOnClickListener(v -> viewModel.setTab(ReceptionScanningUiState.FilterTab.SCANNED));
        binding.tabPending.setOnClickListener(v -> viewModel.setTab(ReceptionScanningUiState.FilterTab.PENDING));

        binding.btnManualEntry.setOnClickListener(v -> showManualEntryDialog());
        binding.btnFinishReception.setOnClickListener(v -> viewModel.finishReception());
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading || state.isSaving ? View.VISIBLE : View.GONE);
            binding.btnFinishReception.setEnabled(!state.isLoading && !state.isSaving);

            if (state.errorMessage != null) {
                Toast.makeText(this, state.errorMessage, Toast.LENGTH_LONG).show();
            }

            if (state.isFinished) {
                Toast.makeText(this, "¡Recepción guardada exitosamente en Firebase!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            if (state.orderDetail != null) {
                PurchaseOrderDetail detail = state.orderDetail;
                binding.tvOrderCode.setText(detail.getCode());
                adapter.submitList(viewModel.getFilteredItems());
                updateTabStyles(state.currentTab);
            }
        });
    }

    private void updateTabStyles(ReceptionScanningUiState.FilterTab currentTab) {
        binding.tabAll.setBackgroundResource(currentTab == ReceptionScanningUiState.FilterTab.ALL ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        binding.tabAll.setTextColor(getColor(currentTab == ReceptionScanningUiState.FilterTab.ALL ? R.color.on_accent : R.color.text_primary));

        binding.tabScanned.setBackgroundResource(currentTab == ReceptionScanningUiState.FilterTab.SCANNED ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        binding.tabScanned.setTextColor(getColor(currentTab == ReceptionScanningUiState.FilterTab.SCANNED ? R.color.on_accent : R.color.text_primary));

        binding.tabPending.setBackgroundResource(currentTab == ReceptionScanningUiState.FilterTab.PENDING ? R.drawable.bg_tab_selected : R.drawable.bg_tab_unselected);
        binding.tabPending.setTextColor(getColor(currentTab == ReceptionScanningUiState.FilterTab.PENDING ? R.color.on_accent : R.color.text_primary));
    }

    // ------------------------------------------------------------------
    // Permiso de cámara
    // ------------------------------------------------------------------

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void checkCameraPermission() {
        if (hasCameraPermission()) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permiso de cámara")
                .setMessage("Para escanear códigos necesitas permitir el acceso a la cámara. "
                        + "Puedes habilitarlo en Ajustes, o ingresar los códigos manualmente.")
                .setPositiveButton("Abrir ajustes", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Ahora no", null)
                .show();
    }

    // ------------------------------------------------------------------
    // Cámara y escaneo
    // ------------------------------------------------------------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // COMPATIBLE evita la vista en negro en algunos dispositivos/layouts.
                binding.cameraPreviewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreviewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (isScanningPaused) {
                        imageProxy.close();
                        return;
                    }

                    @SuppressWarnings("UnsafeOptInUsageError")
                    android.media.Image mediaImage = imageProxy.getImage();
                    if (mediaImage != null) {
                        InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                        barcodeScanner.process(image)
                                .addOnSuccessListener(barcodes -> {
                                    if (!barcodes.isEmpty() && !isScanningPaused) {
                                        for (Barcode barcode : barcodes) {
                                            String rawValue = barcode.getRawValue();
                                            if (rawValue != null && !rawValue.isEmpty()) {
                                                onBarcodeScanned(rawValue);
                                                break;
                                            }
                                        }
                                    }
                                })
                                .addOnCompleteListener(task -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
                cameraStarted = true;
                Log.d(TAG, "Cámara iniciada");

            } catch (Exception e) {
                // Incluye IllegalStateException / IllegalArgumentException (p. ej. sin cámara en emulador).
                Log.e(TAG, "No se pudo iniciar la cámara", e);
                Toast.makeText(this, "Error al iniciar la cámara: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onBarcodeScanned(String barcode) {
        isScanningPaused = true;
        runOnUiThread(() -> {
            PurchaseOrderItem item = viewModel.findMatchingItem(barcode);
            if (item != null) {
                showQuantityDialog(item);
            } else {
                Toast.makeText(this, "Código " + barcode + " no pertenece a esta orden.", Toast.LENGTH_SHORT).show();
                binding.cameraPreviewView.postDelayed(() -> isScanningPaused = false, 1500);
            }
        });
    }

    // ------------------------------------------------------------------
    // Ingreso manual
    // ------------------------------------------------------------------

    private void showManualEntryDialog() {
        isScanningPaused = true;

        final EditText input = new EditText(this);
        input.setHint(R.string.reception_manual_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.reception_manual_entry)
                .setView(container)
                .setPositiveButton(R.string.reception_manual_search, (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.isEmpty()) {
                        isScanningPaused = false;
                        return;
                    }
                    // Mismo camino que un escaneo: busca el ítem y abre el diálogo de cantidad.
                    onBarcodeScanned(code);
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> isScanningPaused = false)
                .setOnCancelListener(d -> isScanningPaused = false)
                .show();
    }

    // ------------------------------------------------------------------
    // Diálogo de cantidad
    // ------------------------------------------------------------------

    private void showQuantityDialog(PurchaseOrderItem item) {
        isScanningPaused = true;

        DialogQuantityInputBinding dialogBinding = DialogQuantityInputBinding.inflate(getLayoutInflater());
        dialogBinding.tvDialogTitle.setText("Producto Detectado");
        dialogBinding.tvDialogSubtitle.setText(item.getProductName());
        dialogBinding.tvDialogMeta.setText("Recibido: " + item.getReceivedQuantity() + " / " + item.getOrderedQuantity() + " un. · SKU: " + item.getProductCode());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .setOnDismissListener(d -> isScanningPaused = false)
                .create();

        dialogBinding.btnDialogMinus.setOnClickListener(v -> {
            try {
                int current = Integer.parseInt(dialogBinding.etQuantity.getText().toString().trim());
                if (current > 1) {
                    dialogBinding.etQuantity.setText(String.valueOf(current - 1));
                }
            } catch (Exception ignored) {}
        });

        dialogBinding.btnDialogPlus.setOnClickListener(v -> {
            try {
                int current = Integer.parseInt(dialogBinding.etQuantity.getText().toString().trim());
                dialogBinding.etQuantity.setText(String.valueOf(current + 1));
            } catch (Exception ignored) {}
        });

        dialogBinding.btnConfirmQuantity.setOnClickListener(v -> {
            try {
                int qtyToAdd = Integer.parseInt(dialogBinding.etQuantity.getText().toString().trim());
                if (qtyToAdd > 0) {
                    viewModel.addQuantityToItem(item.getId(), qtyToAdd);
                    Toast.makeText(this, "+" + qtyToAdd + " un. sumadas a " + item.getProductName(), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Ingresa una cantidad válida.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}
