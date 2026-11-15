package com.example.erprukainventario.dispatch.presentation;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.dispatch.data.DispatchRepository;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.picking.domain.PickingStatus;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class DispatchListViewModel extends TaskListViewModel<PickingOrder> {

    static final int TAB_READY = 0;

    private final DispatchRepository repository;

    @Inject
    public DispatchListViewModel(DispatchRepository repository) {
        this.repository = repository;
        load();
    }

    @Override
    protected void fetch(RepositoryCallback<List<PickingOrder>> callback) {
        repository.getOrders(callback);
    }

    @Override
    protected boolean belongsToTab(PickingOrder order, int tab) {
        return tab == TAB_READY
                ? order.getStatus().isReadyToDispatch()
                : order.getStatus() == PickingStatus.DESPACHADO;
    }

    @Override
    protected TaskSummary toSummary(PickingOrder o) {
        if (o.getStatus() == PickingStatus.DESPACHADO) {
            return new TaskSummary(o.getId(), o.getCode(), o.getCustomerName(),
                    o.getGuideNumber() + " · " + formatDate(o) + " · " + o.getPlate(),
                    "Despachado", TaskSummary.Badge.SUCCESS);
        }
        boolean partial = o.getStatus() == PickingStatus.PARCIAL;
        return new TaskSummary(o.getId(), o.getCode(), o.getCustomerName() + " · " + o.getLineCount() + " líneas",
                "Preparado por " + (o.getAssignedToName().isEmpty() ? "—" : o.getAssignedToName()),
                partial ? "Parcial" : "Listo", partial ? TaskSummary.Badge.WARNING : TaskSummary.Badge.NEUTRAL);
    }

    @Override
    protected String idOf(PickingOrder item) {
        return item.getId();
    }

    static String formatDate(PickingOrder o) {
        return o.getDispatchedAt() == null ? "pendiente de sincronizar"
                : new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(o.getDispatchedAt());
    }
}
