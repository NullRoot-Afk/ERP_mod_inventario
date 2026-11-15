package com.example.erprukainventario.reception.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ActivityReceptionDetailBinding;
import com.example.erprukainventario.databinding.DialogItemFormBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ReceptionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ORDER_ID = "extra_order_id";

    private ActivityReceptionDetailBinding binding;
    private ReceptionDetailViewModel viewModel;
    private ReceptionItemAdapter adapter;
    private String orderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReceptionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ReceptionDetailViewModel.class);
        orderId = getIntent().getStringExtra(EXTRA_ORDER_ID);

        setupList();
        setupListeners();
        observeUiState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recarga al volver del formulario de edición: el cambio se ve de inmediato.
        if (orderId != null) viewModel.loadOrderDetail(orderId);
    }

    private void setupList() {
        adapter = new ReceptionItemAdapter(new ReceptionItemAdapter.OnItemActionListener() {
            @Override public void onEdit(PurchaseOrderItem item) { showItemDialog(item); }
            @Override public void onDelete(PurchaseOrderItem item) { confirmDeleteItem(item); }
        });
        binding.rvProducts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProducts.setAdapter(adapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnStartReception.setOnClickListener(v -> {
            PurchaseOrderDetail detail = currentDetail();
            if (detail != null) {
                Intent intent = new Intent(this, ReceptionScanningActivity.class);
                intent.putExtra(ReceptionScanningActivity.EXTRA_ORDER_ID, detail.getId());
                startActivity(intent);
            }
        });

        binding.btnEditOrder.setOnClickListener(v -> {
            Intent intent = new Intent(this, ReceptionFormActivity.class);
            intent.putExtra(ReceptionFormActivity.EXTRA_ORDER_ID, orderId);
            startActivity(intent);
        });
        binding.btnDeleteOrder.setOnClickListener(v -> confirmDeleteOrder());
        // Solo supervisor / jefe de bodega elimina (las reglas del servidor lo rechazarían igual).
        binding.btnDeleteOrder.setVisibility(viewModel.canDelete() ? View.VISIBLE : View.GONE);
        binding.btnAddItem.setOnClickListener(v -> showItemDialog(null));
    }

    private void observeUiState() {
        viewModel.getUiState().observe(this, state -> {
            binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);

            if (state.orderDetail != null) {
                PurchaseOrderDetail detail = state.orderDetail;
                binding.tvOrderCode.setText(detail.getCode());
                binding.tvSupplierName.setText(detail.getSupplierName());
                binding.tvEtaLabel.setText(detail.getEtaLabel());
                binding.tvNotes.setText(detail.getNotes().isEmpty() ? "Sin notas" : detail.getNotes());

                applyStatusBadge(detail.getStatus());
                adapter.submitList(detail.getItems());
            }
        });

        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearMessage();
            }
        });

        viewModel.getDeleted().observe(this, deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                Toast.makeText(this, "Orden eliminada.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private PurchaseOrderDetail currentDetail() {
        ReceptionDetailUiState state = viewModel.getUiState().getValue();
        return state != null ? state.orderDetail : null;
    }

    // ------------------------------------------------------------------
    // Eliminar (siempre con confirmación previa)
    // ------------------------------------------------------------------

    private void confirmDeleteOrder() {
        PurchaseOrderDetail detail = currentDetail();
        if (detail == null) return;
        int lines = detail.getItems() != null ? detail.getItems().size() : 0;

        new AlertDialog.Builder(this)
                .setTitle("Eliminar orden")
                .setMessage("Se eliminará la orden " + detail.getCode() + " y sus " + lines
                        + " productos. Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> viewModel.deleteOrder(orderId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteItem(PurchaseOrderItem item) {
        if (!viewModel.canDelete()) {
            android.widget.Toast.makeText(this, "Solo un supervisor o jefe de bodega puede eliminar productos de la orden.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Eliminar \"" + item.getProductName() + "\" de esta orden?")
                .setPositiveButton("Eliminar", (d, w) -> viewModel.deleteItem(orderId, item.getId()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------
    // Crear / modificar producto (item == null crea; si no, precarga y modifica)
    // ------------------------------------------------------------------

    private void showItemDialog(PurchaseOrderItem item) {
        DialogItemFormBinding form = DialogItemFormBinding.inflate(getLayoutInflater());
        boolean editing = item != null;

        form.tvItemDialogTitle.setText(editing ? "Modificar producto" : "Agregar producto");
        if (editing) {
            form.etItemName.setText(item.getProductName());
            form.etItemCode.setText(item.getProductCode());
            form.etItemBarcode.setText(item.getBarcode());
            form.etItemQty.setText(String.valueOf(item.getOrderedQuantity()));
        }

        AlertDialog dialog = new AlertDialog.Builder(this).setView(form.getRoot()).create();
        form.btnSaveItem.setText(editing ? "Guardar cambios" : "Agregar");
        form.btnSaveItem.setOnClickListener(v -> {
            viewModel.saveItem(orderId,
                    editing ? item.getId() : null,
                    String.valueOf(form.etItemCode.getText()),
                    String.valueOf(form.etItemName.getText()),
                    String.valueOf(form.etItemBarcode.getText()),
                    String.valueOf(form.etItemQty.getText()));
            dialog.dismiss();
        });
        dialog.show();
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
