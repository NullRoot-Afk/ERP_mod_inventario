package com.example.erprukainventario.reception.presentation;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.databinding.ActivityReceptionFormBinding;

import dagger.hilt.android.AndroidEntryPoint;

/** Crear o modificar una orden. Con EXTRA_ORDER_ID se precarga y modifica; sin él, crea. */
@AndroidEntryPoint
public class ReceptionFormActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityReceptionFormBinding binding;
    private ReceptionFormViewModel viewModel;
    private String orderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionFormViewModel.class);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        boolean editing = orderId != null;

        binding.tvFormTitle.setText(editing ? "Modificar orden" : "Nueva orden");
        binding.btnSave.setText(editing ? "Guardar cambios" : "Crear orden");
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> viewModel.save(
                orderId,
                String.valueOf(binding.etCode.getText()),
                String.valueOf(binding.etSupplier.getText()),
                String.valueOf(binding.etEta.getText()),
                String.valueOf(binding.etNotes.getText())));

        observe(editing, savedInstanceState == null);
    }

    private void observe(boolean editing, boolean firstCreation) {
        if (editing && firstCreation) {
            // Precarga los valores actuales (solo la primera vez, para no pisar lo que el usuario ya escribió).
            viewModel.getLoaded().observe(this, detail -> {
                if (detail == null || !String.valueOf(binding.etCode.getText()).isEmpty()) return;
                binding.etCode.setText(detail.getCode());
                binding.etSupplier.setText(detail.getSupplierName());
                binding.etEta.setText(detail.getEtaLabel());
                binding.etNotes.setText(detail.getNotes());
            });
            viewModel.load(orderId);
        }

        viewModel.getSaving().observe(this, saving -> {
            binding.progressBar.setVisibility(Boolean.TRUE.equals(saving) ? View.VISIBLE : View.GONE);
            binding.btnSave.setEnabled(!Boolean.TRUE.equals(saving));
        });

        viewModel.getError().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getSaved().observe(this, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Toast.makeText(this, orderId == null ? "Orden creada." : "Orden actualizada.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
