package com.example.erprukainventario.home.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.adjustment.data.AdjustmentRepository;
import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.UserRole;
import com.example.erprukainventario.auth.data.AuthRepository;
import com.example.erprukainventario.auth.data.SessionManager;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.count.data.CountRepository;
import com.example.erprukainventario.count.domain.CountStatus;
import com.example.erprukainventario.count.domain.CountTask;
import com.example.erprukainventario.picking.data.PickingRepository;
import com.example.erprukainventario.picking.domain.PickingOrder;
import com.example.erprukainventario.transfer.data.TransferRepository;
import com.example.erprukainventario.transfer.domain.Transfer;
import com.example.erprukainventario.transfer.domain.TransferStatus;


import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class HomeViewModel extends ViewModel {

    private static final int SOURCES = 4;

    private final MutableLiveData<HomeUiState> uiState = new MutableLiveData<>();
    private final PickingRepository picking;
    private final CountRepository counts;
    private final TransferRepository transfers;
    private final AdjustmentRepository adjustments;
    private final WorkContext context;
    private final AuthRepository auth;

    // Los callbacks llegan en el hilo principal: no hace falta sincronizar.
    private int pendingSum;
    private int answered;
    private int refreshId;

    @Inject
    public HomeViewModel(SessionManager sessionManager, PickingRepository picking, CountRepository counts,
                         TransferRepository transfers, AdjustmentRepository adjustments, WorkContext context,
                         AuthRepository auth) {
        this.auth = auth;
        this.picking = picking;
        this.counts = counts;
        this.transfers = transfers;
        this.adjustments = adjustments;
        this.context = context;

        AuthUser user = sessionManager.getCurrentUser();

        String fullName = user != null ? user.getFullName() : "";
        String firstName = toFirstName(fullName);
        String roleLabel = user != null ? roleToLabel(user.getRole()) : "";
        String warehouseName = user != null ? user.getWarehouseName() : "";
        String initials = toInitials(fullName);

        uiState.setValue(new HomeUiState(firstName, roleLabel, warehouseName, initials, -1));
    }

    public LiveData<HomeUiState> getUiState() {
        return uiState;
    }

    public void logout() {
        auth.logout();
    }

    /**
     * Tareas asignadas = pedidos por preparar (míos o sin asignar) + conteos programados
     * + transferencias por enviar/recibir + (supervisor) ajustes por aprobar.
     * Se llama en cada onResume para reflejar lo hecho en los módulos.
     */
    public void refreshPendingTasks() {
        final int id = ++refreshId; // descarta respuestas de un refresco anterior
        pendingSum = 0;
        answered = 0;
        String me = context.userId();
        String warehouse = context.warehouseId();

        picking.getOrders(list -> {
            int n = 0;
            for (PickingOrder o : list) {
                if (o.getStatus().isPickable() && (o.getAssignedToId().isEmpty() || o.isAssignedTo(me))) n++;
            }
            accumulate(id, n);
        });
        counts.getCounts(list -> {
            int n = 0;
            for (CountTask c : list) if (c.getStatus() != CountStatus.CERRADO) n++;
            accumulate(id, n);
        });
        transfers.getTransfers(list -> {
            int n = 0;
            for (Transfer t : list) {
                if ((t.getStatus() == TransferStatus.POR_ENVIAR && warehouse.equals(t.getOriginId()))
                        || (t.getStatus() == TransferStatus.EN_TRANSITO && warehouse.equals(t.getDestinationId()))) n++;
            }
            accumulate(id, n);
        });
        if (context.canApproveAdjustments()) {
            adjustments.getAdjustments(list -> {
                int n = 0;
                for (Adjustment a : list) if (a.getStatus() == AdjustmentStatus.PENDIENTE) n++;
                accumulate(id, n);
            });
        } else {
            accumulate(id, 0);
        }
    }

    private void accumulate(int id, int count) {
        if (id != refreshId) return;
        pendingSum += count;
        answered++;
        HomeUiState current = uiState.getValue();
        if (answered == SOURCES && current != null) {
            uiState.setValue(current.withPendingTasks(pendingSum));
        }
    }

    private String toFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        return fullName.trim().split("\\s+")[0];
    }

    private String roleToLabel(UserRole role) {
        if (role == null) return "";
        switch (role) {
            case OPERARIO: return "Operario";
            case SUPERVISOR: return "Supervisor";
            case JEFE_BODEGA: return "Jefe de bodega";
            default: return "";
        }
    }

    private String toInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }
}
