package com.example.erprukainventario.adjustment.presentation;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.core.ui.BaseTaskListActivity;
import com.example.erprukainventario.core.ui.Dialogs;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AdjustmentListActivity extends BaseTaskListActivity {

    private AdjustmentListViewModel vm;

    @Override
    protected TaskListViewModel<?> viewModel() {
        if (vm == null) vm = new ViewModelProvider(this).get(AdjustmentListViewModel.class);
        return vm;
    }

    @Override protected String screenTitle() { return getString(R.string.adjustment_title); }
    @Override protected String searchHint() { return getString(R.string.adjustment_search_hint); }
    @Override protected String emptyText() { return getString(R.string.adjustment_empty); }
    @Override protected String primaryActionLabel() { return getString(R.string.adjustment_new_manual); }

    @Override
    protected String[] tabLabels() {
        return new String[]{
                getString(R.string.adjustment_section_mine),
                // viewModel() y no vm: la base llama a tabLabels() en onCreate, antes de crear el VM.
                getString(((AdjustmentListViewModel) viewModel()).canApprove()
                        ? R.string.adjustment_tab_to_approve : R.string.tab_pending),
                getString(R.string.tab_all)};
    }

    @Override
    protected void onPrimaryAction() {
        startActivity(new Intent(this, AdjustmentFormActivity.class));
    }

    @Override
    protected void onItemClick(TaskSummary item) {
        Adjustment a = vm.findById(item.id);
        if (a == null) return;

        StringBuilder detail = new StringBuilder()
                .append("Producto: ").append(a.getProductName()).append(" (").append(a.getProductCode()).append(")\n")
                .append("Cantidad: ").append(a.signedQuantity()).append(" un.\n")
                .append("Motivo: ").append(a.getReason().getLabel()).append("\n")
                .append("Origen: ").append(a.getReference()).append("\n")
                .append("Reportado por: ").append(a.getCreatedByName()).append(" · ")
                .append(AdjustmentListViewModel.formatDate(a));
        if (!a.getNotes().isEmpty()) detail.append("\nNotas: ").append(a.getNotes());
        if (a.getStatus() != AdjustmentStatus.PENDIENTE) {
            detail.append("\n\n").append(a.getStatus().getLabel()).append(" por ").append(a.getReviewedByName());
            if (!a.getReviewNote().isEmpty()) detail.append(": ").append(a.getReviewNote());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Ajuste · " + a.getStatus().getLabel())
                .setMessage(detail.toString());

        if (a.getStatus() == AdjustmentStatus.PENDIENTE && vm.canApprove()) {
            builder.setPositiveButton("Aprobar", (d, w) -> vm.approve(a))
                    .setNegativeButton("Rechazar", (d, w) -> Dialogs.textInput(this, "Motivo del rechazo",
                            "Ej. recontar antes de ajustar", "Rechazar", note -> vm.reject(a, note), null))
                    .setNeutralButton("Cerrar", null);
        } else {
            if (a.getStatus() == AdjustmentStatus.PENDIENTE) {
                builder.setMessage(detail + "\n\nEsperando aprobación de un supervisor.");
            }
            builder.setPositiveButton(android.R.string.ok, null);
        }
        builder.show();
    }

    @Override
    protected String seedDescription() {
        return "Se crearán 3 ajustes pendientes de prueba (y el catálogo de productos si no existe).";
    }

    @Override
    protected void onSeedConfirmed() {
        vm.seedDemoData();
    }
}
