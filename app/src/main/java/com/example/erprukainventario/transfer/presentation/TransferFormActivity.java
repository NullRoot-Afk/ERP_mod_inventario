package com.example.erprukainventario.transfer.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.scan.ScanLineAdapter;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.databinding.ActivityTransferFormBinding;
import com.example.erprukainventario.lookup.presentation.ProductLookupActivity;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/** Solicitud de traslado desde la bodega del usuario hacia otra bodega de la empresa. */
@AndroidEntryPoint
public class TransferFormActivity extends AppCompatActivity {

    private ActivityTransferFormBinding binding;
    private TransferFormViewModel viewModel;
    private ScanLineAdapter adapter;
    private List<Warehouse> destinations = new ArrayList<>();
    private Warehouse selectedDestination;

    private final ActivityResultLauncher<Intent> scanLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                String code = ProductLookupActivity.pickedCode(result.getResultCode(), result.getData());
                if (code != null) viewModel.findProduct(code, this::askQuantity);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTransferFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(TransferFormViewModel.class);

        binding.tvOrigin.setText(getString(R.string.transfer_origin_format, viewModel.originName()));

        adapter = new ScanLineAdapter(this::editLine);
        binding.rvLines.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLines.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddByCode.setOnClickListener(v -> Dialogs.textInput(this, getString(R.string.transfer_add_code),
                getString(R.string.reception_manual_hint), getString(R.string.reception_manual_search),
                code -> viewModel.findProduct(code, this::askQuantity), null));
        binding.btnAddByScan.setOnClickListener(v -> scanLauncher.launch(ProductLookupActivity.pickIntent(this)));
        binding.actDestination.setOnItemClickListener((parent, view, position, id) ->
                selectedDestination = destinations.get(position));
        binding.btnSave.setOnClickListener(v -> viewModel.create(selectedDestination,
                binding.etNotes.getText() != null ? binding.etNotes.getText().toString().trim() : ""));

        observe();
    }

    private void observe() {
        viewModel.getDestinations().observe(this, list -> {
            destinations = list;
            binding.actDestination.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list));
        });
        viewModel.getLines().observe(this, lines -> {
            adapter.submit(lines, false);
            binding.tvNoLines.setVisibility(lines.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.isBusy().observe(this, busy -> {
            binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            binding.btnSave.setEnabled(!busy);
        });
        viewModel.getMessages().observe(this, event -> {
            String msg = event.consume();
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        viewModel.isCreated().observe(this, created -> {
            if (created) finish();
        });
    }

    private void askQuantity(Product p) {
        Dialogs.quantity(this, getString(R.string.transfer_add_title), p.getName(),
                getString(R.string.transfer_stock_format, p.stockIn(viewModel.originId()), p.getLocation()),
                getString(R.string.transfer_quantity_label), getString(R.string.reception_add_button), 1, 1,
                qty -> viewModel.addProduct(p, qty), null);
    }

    private void editLine(ScanLine line) {
        Dialogs.quantity(this, getString(R.string.scan_edit_title), line.getProductName(),
                getString(R.string.transfer_edit_hint), getString(R.string.transfer_quantity_label),
                getString(R.string.scan_edit_confirm), line.getScannedQuantity(), 0,
                qty -> viewModel.updateLine(line, qty), null);
    }
}
