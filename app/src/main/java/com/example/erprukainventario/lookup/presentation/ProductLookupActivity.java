package com.example.erprukainventario.lookup.presentation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.adjustment.presentation.AdjustmentFormActivity;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.BarcodeScannerController;
import com.example.erprukainventario.core.scan.ScanFeedback;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.databinding.ActivityProductLookupBinding;

import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * "Escanear ahora": consulta rápida de un producto (ubicación, stock por bodega,
 * alerta de stock crítico e historial de movimientos).
 *
 * En modo selección ({@link #pickIntent}) devuelve el código del producto
 * encontrado a quien la abrió (ej. el formulario de ajustes).
 */
@AndroidEntryPoint
public class ProductLookupActivity extends AppCompatActivity {

    private static final String EXTRA_PICK_MODE = "extra_pick_mode";
    private static final String EXTRA_RESULT_CODE = "extra_result_code";

    private ActivityProductLookupBinding binding;
    private ProductLookupViewModel viewModel;
    private BarcodeScannerController scanner;
    private ScanFeedback feedback;
    private final MovementAdapter movementAdapter = new MovementAdapter();
    private boolean pickMode;

    public static Intent pickIntent(Context context) {
        return new Intent(context, ProductLookupActivity.class).putExtra(EXTRA_PICK_MODE, true);
    }

    /** Lee el resultado del modo selección; null si se canceló. */
    public static String pickedCode(int resultCode, Intent data) {
        return resultCode == Activity.RESULT_OK && data != null ? data.getStringExtra(EXTRA_RESULT_CODE) : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProductLookupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pickMode = getIntent().getBooleanExtra(EXTRA_PICK_MODE, false);
        viewModel = new ViewModelProvider(this).get(ProductLookupViewModel.class);
        feedback = new ScanFeedback(this);
        scanner = new BarcodeScannerController(this, binding.cameraPreviewView, this::onCode);

        binding.tvTitle.setText(pickMode ? R.string.lookup_pick_title : R.string.lookup_title);
        binding.rvMovements.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMovements.setAdapter(movementAdapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnManualEntry.setOnClickListener(v -> {
            scanner.pause();
            Dialogs.textInput(this, getString(R.string.scan_manual_entry), getString(R.string.reception_manual_hint),
                    getString(R.string.reception_manual_search), this::onCode, scanner::resume);
        });

        observe();
        scanner.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        feedback.release();
    }

    private void onCode(String code) {
        viewModel.lookup(code);
    }

    private void observe() {
        viewModel.isLoading().observe(this, loading ->
                binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getNotFound().observe(this, event -> {
            String code = event.consume();
            if (code == null) return;
            feedback.error();
            Toast.makeText(this, getString(R.string.lookup_not_found_format, code), Toast.LENGTH_SHORT).show();
            binding.cameraPreviewView.postDelayed(scanner::resume, 1200);
        });

        viewModel.getProduct().observe(this, product -> {
            if (product == null) return;
            if (pickMode) {
                feedback.success();
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_CODE, product.getCode()));
                finish();
                return;
            }
            showProduct(product);
        });

        viewModel.getMovements().observe(this, list -> {
            movementAdapter.submit(list);
            binding.tvNoMovements.setVisibility(list.isEmpty() && viewModel.getProduct().getValue() != null
                    ? View.VISIBLE : View.GONE);
        });
    }

    private void showProduct(Product p) {
        feedback.success();
        binding.tvHint.setVisibility(View.GONE);
        binding.scrollResult.setVisibility(View.VISIBLE);

        String myWarehouse = viewModel.myWarehouseId();
        binding.tvLocation.setText(p.getLocation());
        binding.tvName.setText(p.getName());
        binding.tvCodes.setText("SKU " + p.getCode() + " · EAN " + p.getBarcode()
                + (p.getCategory().isEmpty() ? "" : " · " + p.getCategory()));
        binding.tvStock.setText(getString(R.string.lookup_units_format, p.stockIn(myWarehouse)));
        binding.tvStockLabel.setText(getString(R.string.lookup_stock_label_format,
                viewModel.myWarehouseName(), p.getMinStock()));

        boolean low = p.isLowStockIn(myWarehouse);
        binding.tvLowStock.setVisibility(low ? View.VISIBLE : View.GONE);
        binding.cardProduct.setBackgroundResource(low ? R.drawable.bg_carg_danger : R.drawable.bg_card_default);

        StringBuilder others = new StringBuilder();
        for (Map.Entry<String, Integer> e : p.getStockByWarehouse().entrySet()) {
            if (e.getKey().equals(myWarehouse)) continue;
            if (others.length() > 0) others.append('\n');
            others.append(viewModel.warehouseName(e.getKey())).append(": ").append(e.getValue()).append(" un.");
        }
        binding.tvOtherWarehouses.setText(others.length() == 0
                ? getString(R.string.lookup_no_other_stock) : others.toString());

        binding.btnReportAdjustment.setOnClickListener(v ->
                startActivity(AdjustmentFormActivity.intent(this, p.getCode())));

        // Deja la cámara lista para el siguiente producto tras un momento.
        binding.cameraPreviewView.postDelayed(scanner::resume, 1500);
    }
}
