package com.example.erprukainventario.dispatch.presentation;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanSessionActivity;
import com.example.erprukainventario.core.ui.BaseTaskListActivity;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.picking.domain.PickingStatus;

import dagger.hilt.android.AndroidEntryPoint;

/** Pedidos preparados listos para salir + historial de guías emitidas. */
@AndroidEntryPoint
public class DispatchListActivity extends BaseTaskListActivity {

    private DispatchListViewModel vm;

    @Override
    protected TaskListViewModel<?> viewModel() {
        if (vm == null) vm = new ViewModelProvider(this).get(DispatchListViewModel.class);
        return vm;
    }

    @Override protected String screenTitle() { return getString(R.string.dispatch_title); }
    @Override protected String searchHint() { return getString(R.string.dispatch_search_hint); }
    @Override protected String emptyText() { return getString(R.string.dispatch_empty); }

    @Override
    protected String[] tabLabels() {
        return new String[]{getString(R.string.tab_ready_to_dispatch), getString(R.string.tab_history)};
    }

    @Override
    protected void onItemClick(TaskSummary item) {
        PickingOrder order = vm.findById(item.id);
        if (order == null) return;
        if (order.getStatus() == PickingStatus.DESPACHADO) {
            showGuide(order);
        } else {
            startActivity(ScanSessionActivity.intent(this, ScanModes.DISPATCH, order.getId()));
        }
    }

    /** Guía de despacho (guía §6): se puede compartir como texto con el transportista. */
    private void showGuide(PickingOrder order) {
        String text = "GUÍA DE DESPACHO " + order.getGuideNumber() + "\n"
                + "Pedido: " + order.getCode() + "\n"
                + "Cliente: " + order.getCustomerName() + "\n"
                + "Fecha: " + DispatchListViewModel.formatDate(order) + "\n"
                + "Transportista: " + order.getCarrier() + "\n"
                + "Patente: " + order.getPlate() + "\n"
                + "Líneas: " + order.getLineCount();

        new AlertDialog.Builder(this)
                .setTitle(order.getGuideNumber())
                .setMessage(text)
                .setPositiveButton("Compartir", (d, w) -> {
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Guía " + order.getGuideNumber())
                            .putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(send, "Compartir guía"));
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }
}
