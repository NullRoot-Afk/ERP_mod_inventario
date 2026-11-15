package com.example.erprukainventario.picking.presentation;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanSessionActivity;
import com.example.erprukainventario.core.ui.BaseTaskListActivity;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.picking.domain.PickingOrder;

import dagger.hilt.android.AndroidEntryPoint;

/** Lista de pedidos a preparar. Al tocar uno se toma y se abre el escaneo en orden de ubicación. */
@AndroidEntryPoint
public class PickingListActivity extends BaseTaskListActivity {

    private PickingListViewModel vm;

    @Override
    protected TaskListViewModel<?> viewModel() {
        if (vm == null) vm = new ViewModelProvider(this).get(PickingListViewModel.class);
        return vm;
    }

    @Override protected String screenTitle() { return getString(R.string.picking_title); }
    @Override protected String searchHint() { return getString(R.string.picking_search_hint); }
    @Override protected String emptyText() { return getString(R.string.picking_empty); }

    @Override
    protected String[] tabLabels() {
        return new String[]{getString(R.string.tab_pending), getString(R.string.tab_assigned_to_me), getString(R.string.tab_all)};
    }

    @Override
    protected void onItemClick(TaskSummary item) {
        PickingOrder order = vm.findById(item.id);
        if (order == null) return;

        if (!order.getStatus().isPickable()) {
            new AlertDialog.Builder(this)
                    .setTitle(order.getCode())
                    .setMessage("Este pedido está " + order.getStatus().getLabel().toLowerCase()
                            + ". Continúa su gestión desde Despacho.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (vm.isAssignedToOther(order)) {
            new AlertDialog.Builder(this)
                    .setTitle("Pedido en curso")
                    .setMessage(order.getAssignedToName() + " está preparando este pedido. ¿Quieres tomarlo tú?")
                    .setPositiveButton("Tomar pedido", (d, w) -> openPicking(order))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        openPicking(order);
    }

    private void openPicking(PickingOrder order) {
        vm.takeOrder(order, () -> startActivity(ScanSessionActivity.intent(this, ScanModes.PICKING, order.getId())));
    }

    @Override
    protected String seedDescription() {
        return "Se crearán 4 pedidos de prueba (y el catálogo de productos si no existe).";
    }

    @Override
    protected void onSeedConfirmed() {
        vm.seedDemoData();
    }
}
