package com.example.erprukainventario.reception.presentation;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityReceptionProcessBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReceptionProcessActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityReceptionProcessBinding binding;
    private ReceptionProcessViewModel viewModel;
    private ReceptionProcessAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionProcessBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionProcessViewModel.class);

        setupList();
        setupListeners();
        observeUiState();

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        viewModel.loadOrderDetail(orderId);
    }

    private void setupList() {
        adapter = new ReceptionProcessAdapter((item, newReceivedQty) ->
                viewModel.updateItemQuantity(item.getId(), newReceivedQty)
        );
        binding.rvProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProducts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
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
                Toast.makeText(this, "¡Recepción registrada exitosamente en Firebase!", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            if (state.orderDetail != null) {
                PurchaseOrderDetail detail = state.orderDetail;
                binding.tvOrderCode.setText(detail.getCode());
                adapter.submitList(detail.getItems());

                updateProgressSummary(detail);
            }
        });
    }

    private void updateProgressSummary(PurchaseOrderDetail detail) {
        int ordered = 0;
        int received = 0;

        if (detail.getItems() != null) {
            for (PurchaseOrderItem item : detail.getItems()) {
                ordered += item.getOrderedQuantity();
                received += item.getReceivedQuantity();
            }
        }

        binding.tvProgressSummary.setText(getString(R.string.reception_units_format, received, ordered));

        int percent = ordered > 0 ? (received * 100) / ordered : 0;
        binding.pbOverallProgress.setProgress(percent);
    }
}
