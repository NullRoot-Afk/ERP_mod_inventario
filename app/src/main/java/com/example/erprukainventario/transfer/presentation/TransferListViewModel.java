package com.example.erprukainventario.transfer.presentation;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.transfer.data.TransferRepository;
import com.example.erprukainventario.transfer.domain.Transfer;
import com.example.erprukainventario.transfer.domain.TransferStatus;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TransferListViewModel extends TaskListViewModel<Transfer> {

    static final int TAB_TO_SEND = 0;
    static final int TAB_TO_RECEIVE = 1;

    private final TransferRepository repository;
    private final WorkContext context;

    @Inject
    public TransferListViewModel(TransferRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
        load();
    }

    public boolean isToSend(Transfer t) {
        return t.getStatus() == TransferStatus.POR_ENVIAR && context.warehouseId().equals(t.getOriginId());
    }

    public boolean isToReceive(Transfer t) {
        return t.getStatus() == TransferStatus.EN_TRANSITO && context.warehouseId().equals(t.getDestinationId());
    }

    @Override
    protected void fetch(RepositoryCallback<List<Transfer>> callback) {
        repository.getTransfers(callback);
    }

    @Override
    protected boolean belongsToTab(Transfer t, int tab) {
        if (tab == TAB_TO_SEND) return isToSend(t);
        if (tab == TAB_TO_RECEIVE) return isToReceive(t);
        return !isToSend(t) && !isToReceive(t);
    }

    @Override
    protected TaskSummary toSummary(Transfer t) {
        boolean outgoing = context.warehouseId().equals(t.getOriginId());
        String route = outgoing ? "Hacia " + t.getDestinationName() : "Desde " + t.getOriginName();
        TaskSummary.Badge badge;
        String label = t.getStatus().getLabel();
        if (t.getStatus() == TransferStatus.RECIBIDA) {
            badge = t.hasDiscrepancy() ? TaskSummary.Badge.DANGER : TaskSummary.Badge.SUCCESS;
            if (t.hasDiscrepancy()) label = "Con diferencias";
        } else if (t.getStatus() == TransferStatus.EN_TRANSITO) {
            badge = TaskSummary.Badge.WARNING;
        } else {
            badge = TaskSummary.Badge.NEUTRAL;
        }
        return new TaskSummary(t.getId(), t.getCode(), route + " · " + t.getLineCount() + " líneas",
                "Creada por " + t.getCreatedByName() + (t.getCarrier().isEmpty() ? "" : " · " + t.getCarrier()),
                label, badge);
    }

    @Override
    protected String idOf(Transfer item) {
        return item.getId();
    }

    public void seedDemoData() {
        repository.seedDemoData(reloadAfter("Transferencias de prueba cargadas.", "No se pudieron cargar los datos."));
    }
}
