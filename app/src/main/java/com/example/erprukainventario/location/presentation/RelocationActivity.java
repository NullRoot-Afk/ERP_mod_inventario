package com.example.erprukainventario.location.presentation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.BarcodeScannerController;
import com.example.erprukainventario.core.scan.ScanFeedback;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.databinding.ActivityRelocationBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Reubicación de productos con escaneo doble: código de producto + código de
 * ubicación (etiqueta del estante, ej. "LOC:A-03-02-B").
 */
@AndroidEntryPoint
public class RelocationActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUCT_CODE = "extra_product_code";

    private ActivityRelocationBinding binding;
    private RelocationViewModel viewModel;
    private BarcodeScannerController scanner;
    private ScanFeedback feedback;

    /** Abre la reubicación con el producto ya elegido (desde la consulta de producto). */
    public static Intent intent(Context context, String productCode) {
        Intent i = new Intent(context, RelocationActivity.class);
        if (productCode != null) i.putExtra(EXTRA_PRODUCT_CODE, productCode);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRelocationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(RelocationViewModel.class);
        feedback = new ScanFeedback(this);
        scanner = new BarcodeScannerController(this, binding.cameraPreviewView, viewModel::onCode);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnManualEntry.setOnClickListener(v -> {
            scanner.pause();
            boolean location = viewModel.isWaitingLocation();
            Dialogs.textInput(this,
                    getString(location ? R.string.relocation_manual_location : R.string.scan_manual_entry),
                    getString(location ? R.string.relocation_location_hint : R.string.reception_manual_hint),
                    getString(R.string.reception_manual_search), viewModel::onCode, scanner::resume);
        });
        binding.btnChangeProduct.setOnClickListener(v -> viewModel.clearProduct());

        observe();
        scanner.start();

        String preset = getIntent().getStringExtra(EXTRA_PRODUCT_CODE);
        if (savedInstanceState == null && preset != null) viewModel.findProduct(preset);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        feedback.release();
    }

    private void observe() {
        viewModel.isLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getProduct().observe(this, this::render);

        viewModel.getError().observe(this, event -> {
            String message = event.consume();
            if (message == null) return;
            feedback.error();
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            binding.cameraPreviewView.postDelayed(scanner::resume, 1200);
        });

        viewModel.getPendingConfirmation().observe(this, event -> {
            String target = event.consume();
            Product p = viewModel.getProduct().getValue();
            if (target == null || p == null) return;
            feedback.success();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.relocation_confirm_title)
                    .setMessage(getString(R.string.relocation_confirm_format, p.getName(),
                            p.getLocation(), target, viewModel.stockOf(p)))
                    .setPositiveButton(R.string.relocation_confirm, (d, w) -> viewModel.confirm(target))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener(d -> scanner.resume())
                    .show();
        });

        viewModel.getResult().observe(this, event -> {
            if (event == null) return;
            com.example.erprukainventario.core.domain.OperationResult r = event.consume();
            if (r == null) return;
            if (r.isSuccess()) feedback.success(); else feedback.error();
            Toast.makeText(this, r.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void render(Product p) {
        boolean hasProduct = p != null;
        binding.tvStep.setText(hasProduct ? R.string.relocation_step_location : R.string.relocation_step_product);
        binding.cardProduct.setVisibility(hasProduct ? View.VISIBLE : View.GONE);
        binding.btnChangeProduct.setVisibility(hasProduct ? View.VISIBLE : View.GONE);
        binding.btnManualEntry.setText(hasProduct ? R.string.relocation_manual_location_short : R.string.lookup_manual);
        if (!hasProduct) {
            scanner.resume();
            return;
        }
        feedback.success();
        binding.tvName.setText(p.getName());
        binding.tvCodes.setText("SKU " + p.getCode() + " · EAN " + p.getBarcode());
        binding.tvCurrentLocation.setText(p.getLocation());
        binding.tvStock.setText(getString(R.string.lookup_units_format, viewModel.stockOf(p)));
        binding.cameraPreviewView.postDelayed(scanner::resume, 1000);
    }
}
