package com.example.erprukainventario.transfer.presentation;

import android.content.Intent;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.erprukainventario.R;
import com.example.erprukainventario.core.scan.ScanModes;
import com.example.erprukainventario.core.scan.ScanSessionActivity;
import com.example.erprukainventario.core.ui.BaseTaskListActivity;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.transfer.domain.Transfer;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TransferListActivity extends BaseTaskListActivity {

    private TransferListViewModel vm;

    @Override
    protected TaskListViewModel<?> viewModel() {
        if (vm == null) vm = new ViewModelProvider(this).get(TransferListViewModel.class);
        return vm;
    }

    @Override protected String screenTitle() { return getString(R.string.transfer_title); }
    @Override protected String searchHint() { return getString(R.string.transfer_search_hint); }
    @Override protected String emptyText() { return getString(R.string.transfer_empty); }
    @Override protected String primaryActionLabel() { return getString(R.string.transfer_new); }

    @Override
    protected String[] tabLabels() {
        return new String[]{getString(R.string.tab_to_send), getString(R.string.tab_to_receive), getString(R.string.tab_history)};
    }

    @Override
    protected void onPrimaryAction() {
        startActivity(new Intent(this, TransferFormActivity.class));
    }

    @Override
    protected void onItemClick(TaskSummary item) {
        Transfer t = vm.findById(item.id);
        if (t == null) return;
        if (vm.isToSend(t)) {
            startActivity(ScanSessionActivity.intent(this, ScanModes.TRANSFER_SEND, t.getId()));
        } else if (vm.isToReceive(t)) {
            startActivity(ScanSessionActivity.intent(this, ScanModes.TRANSFER_RECEIVE, t.getId()));
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(t.getCode() + " · " + t.getStatus().getLabel())
                    .setMessage("Origen: " + t.getOriginName() + "\nDestino: " + t.getDestinationName()
                            + "\nLíneas: " + t.getLineCount()
                            + (t.getCarrier().isEmpty() ? "" : "\nTransportista: " + t.getCarrier())
                            + (t.getNotes().isEmpty() ? "" : "\nNotas: " + t.getNotes())
                            + (t.hasDiscrepancy() ? "\n\nSe recibió con diferencias respecto de lo enviado." : ""))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    @Override
    protected String seedDescription() {
        return "Se crearán 2 transferencias por enviar y 1 por recibir (y el catálogo/bodegas si no existen).";
    }

    @Override
    protected void onSeedConfirmed() {
        vm.seedDemoData();
    }
}
