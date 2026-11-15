package com.example.erprukainventario.adjustment.presentation;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.databinding.ActivityAdjustmentFormBinding;
import com.example.erprukainventario.lookup.presentation.ProductLookupActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

/** Registro de merma, vencido, dañado, extraviado, etc. Queda PENDIENTE hasta que un supervisor apruebe. */
@AndroidEntryPoint
public class AdjustmentFormActivity extends AppCompatActivity {

    private static final String EXTRA_PRODUCT_CODE = "extra_product_code";

    private ActivityAdjustmentFormBinding binding;
    private AdjustmentFormViewModel viewModel;
    private AdjustmentReason selectedReason;
    private Lot selectedLot;

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                String code = ProductLookupActivity.pickedCode(result.getResultCode(), result.getData());
                if (code != null) {
                    binding.etProductCode.setText(code);
                    viewModel.lookup(code);
                }
            });

    /** Abre el formulario con el producto ya buscado (desde "Escanear ahora"). */
    public static Intent intent(Context context, String productCode) {
        return new Intent(context, AdjustmentFormActivity.class).putExtra(EXTRA_PRODUCT_CODE, productCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdjustmentFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(AdjustmentFormViewModel.class);

        setupReasons();
        setupListeners();
        observe();

        String code = getIntent().getStringExtra(EXTRA_PRODUCT_CODE);
        if (savedInstanceState == null && code != null) {
            binding.etProductCode.setText(code);
            viewModel.lookup(code);
        }
    }

    private void setupReasons() {
        ArrayAdapter<AdjustmentReason> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, AdjustmentReason.values());
        binding.actReason.setAdapter(adapter);
        binding.actReason.setOnItemClickListener((parent, view, position, id) -> {
            selectedReason = adapter.getItem(position);
            // La dirección solo se elige cuando el motivo no la define (ej. "Otro").
            binding.toggleDirection.setVisibility(
                    selectedReason != null && selectedReason.getSign() == 0 ? View.VISIBLE : View.GONE);
        });
        binding.toggleDirection.setVisibility(View.GONE);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnScanProduct.setOnClickListener(v -> scanLauncher.launch(ProductLookupActivity.pickIntent(this)));
        binding.btnSearchProduct.setOnClickListener(v -> viewModel.lookup(text(binding.etProductCode)));
        binding.etProductCode.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.lookup(text(binding.etProductCode));
                return true;
            }
            return false;
        });
        binding.btnSave.setOnClickListener(v -> viewModel.submit(selectedReason,
                binding.toggleDirection.getCheckedButtonId() == R.id.btnOut,
                text(binding.etQuantity), text(binding.etNotes), selectedLot, text(binding.etSerials)));
    }

    private void observe() {
        viewModel.getProduct().observe(this, this::showProduct);
        viewModel.getLots().observe(this, this::showLots);
        viewModel.isBusy().observe(this, busy -> {
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnSave.setEnabled(!busy);
        });
        viewModel.getMessages().observe(this, event -> {
            String msg = event.consume();
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        viewModel.isSaved().observe(this, saved -> {
            if (saved) {
                Toast.makeText(this, "Ajuste enviado a aprobación.", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void showProduct(Product p) {
        binding.tilSerials.setVisibility(p != null && p.isSerialized() ? View.VISIBLE : View.GONE);
        if (p == null) {
            binding.tvProductInfo.setText(R.string.adjustment_no_product);
            binding.tvProductInfo.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        binding.tvProductInfo.setText(getString(R.string.adjustment_product_format,
                p.getName(), p.getCode(), p.getLocation(), p.stockIn(viewModel.warehouseId())));
        binding.tvProductInfo.setTextColor(getColor(R.color.text_primary));
    }

    /** Lote afectado (guía §10): opcional; sin indicarlo, la rebaja sale por FEFO al aprobar. */
    private void showLots(List<Lot> lots) {
        selectedLot = null;
        boolean any = lots != null && !lots.isEmpty();
        binding.tilLot.setVisibility(any ? View.VISIBLE : View.GONE);
        if (!any) return;

        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.adjustment_lot_auto));
        for (Lot lot : lots) {
            String expiry = lot.getExpiryDate() != null
                    ? getString(R.string.lot_expires_format, format.format(lot.getExpiryDate()))
                    : getString(R.string.lot_no_expiry);
            labels.add(getString(R.string.adjustment_lot_item_format, lot.getLotNumber(), lot.getQuantity(), expiry));
        }
        binding.actLot.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        binding.actLot.setText(labels.get(0), false);
        binding.actLot.setOnItemClickListener((parent, view, position, id) ->
                selectedLot = position == 0 ? null : lots.get(position - 1));
    }

    private static String text(android.widget.TextView view) {
        return view.getText() != null ? view.getText().toString().trim() : "";
    }
}
