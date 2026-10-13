package com.example.erprukainventario.reception.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityReceptionDetailBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReceptionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityReceptionDetailBinding binding;
    private ReceptionDetailViewModel viewModel;
    private ReceptionItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionDetailViewModel.class);

        setupList();
        setupListeners();
        observeUiState();

        String orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);
        viewModel.loadOrderDetail(orderId);
    }

    private void setupList() {
        adapter = new ReceptionItemAdapter();
        binding.rvProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProducts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnStartReception.setOnClickListener(v -> {
            PurchaseOrderDetail detail = viewModel.getUiState().getValue() != null ? viewModel.getUiState().getValue().orderDetail : null;
            if (detail != null) {
                Intent intent = new Intent(this, ReceptionScanningActivity.class);
                intent.putExtra(ReceptionScanningActivity.EXTRA_ORDER_ID, detail.getId());
                startActivity(intent);
            }
        });
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);

            if (state.orderDetail != null) {
                PurchaseOrderDetail detail = state.orderDetail;
                binding.tvOrderCode.setText(detail.getCode());
                binding.tvSupplierName.setText(detail.getSupplierName());
                binding.tvEtaLabel.setText(detail.getEtaLabel());

                applyStatusBadge(detail.getStatus());
                adapter.submitList(detail.getItems());
            }
        });
    }

    private void applyStatusBadge(PurchaseOrderStatus status) {
        int bgRes;
        int textColorRes;
        String label;

        PurchaseOrderStatus resolvedStatus = status != null ? status : PurchaseOrderStatus.SIN_INICIAR;

        switch (resolvedStatus) {
            case PARCIAL:
                bgRes = R.drawable.bg_badge_warning;
                textColorRes = R.color.text_warning;
                label = "Parcial";
                break;
            case COMPLETA:
                bgRes = R.drawable.bg_badge_success;
                textColorRes = R.color.text_success;
                label = "Completa";
                break;
            case SIN_INICIAR:
            default:
                bgRes = R.drawable.bg_badge_neutral;
                textColorRes = R.color.text_secondary;
                label = "Sin iniciar";
                break;
        }

        binding.tvStatusBadge.setText(label);
        binding.tvStatusBadge.setBackgroundResource(bgRes);
        binding.tvStatusBadge.setTextColor(getColor(textColorRes));
    }
}
