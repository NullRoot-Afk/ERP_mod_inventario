package com.example.erprukainventario.picking.presentation;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;
import com.example.erprukainventario.picking.data.PickingRepository;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.picking.domain.PickingStatus;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class PickingListViewModel extends TaskListViewModel<PickingOrder> {

    static final int TAB_PENDING = 0;
    static final int TAB_MINE = 1;

    private final PickingRepository repository;
    private final WorkContext context;

    @Inject
    public PickingListViewModel(PickingRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
        load();
    }

    @Override
    protected void fetch(RepositoryCallback<List<PickingOrder>> callback) {
        repository.getOrders(callback);
    }

    @Override
    protected boolean belongsToTab(PickingOrder order, int tab) {
        if (tab == TAB_PENDING) return order.getStatus().isPickable();
        if (tab == TAB_MINE) return order.isAssignedTo(context.userId()) && order.getStatus().isPickable();
        return true;
    }

    @Override
    protected TaskSummary toSummary(PickingOrder o) {
        String meta = o.getAssignedToName().isEmpty()
                ? (o.getDueLabel().isEmpty() ? "Sin asignar" : o.getDueLabel() + " · sin asignar")
                : o.getDueLabel() + " · " + o.getAssignedToName();
        TaskSummary.Badge badge;
        switch (o.getStatus()) {
            case PARCIAL:
            case EN_PROCESO:
                badge = TaskSummary.Badge.WARNING;
                break;
            case PREPARADO:
            case DESPACHADO:
                badge = TaskSummary.Badge.SUCCESS;
                break;
            default:
                badge = TaskSummary.Badge.NEUTRAL;
        }
        return new TaskSummary(o.getId(), o.getCode(), o.getCustomerName() + " · " + o.getLineCount() + " líneas",
                meta, o.getStatus().getLabel(), badge);
    }

    @Override
    protected String idOf(PickingOrder item) {
        return item.getId();
    }

    /** Toma el pedido antes de abrir el escaneo, para que otro operario vea que está en curso. */
    public void takeOrder(PickingOrder order, Runnable onTaken) {
        if (order.isAssignedTo(context.userId())) {
            onTaken.run();
            return;
        }
        boolean markInProgress = order.getStatus() == PickingStatus.PENDIENTE;
        repository.assignToMe(order.getId(), markInProgress, ok -> {
            if (ok) onTaken.run();
            else showMessage("No se pudo tomar el pedido. Revisa tu conexión.");
        });
    }

    public boolean isAssignedToOther(PickingOrder order) {
        return !order.getAssignedToId().isEmpty() && !order.isAssignedTo(context.userId())
                && order.getStatus() == PickingStatus.EN_PROCESO;
    }

    public void seedDemoData() {
        repository.seedDemoData(reloadAfter("Pedidos de prueba cargados.", "No se pudieron cargar los datos."));
    }
}
