package com.example.erprukainventario.adjustment.presentation;

import com.example.erprukainventario.adjustment.data.AdjustmentRepository;
import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.ui.TaskListViewModel;
import com.example.erprukainventario.core.ui.TaskSummary;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AdjustmentListViewModel extends TaskListViewModel<Adjustment> {

    static final int TAB_MINE = 0;
    static final int TAB_PENDING = 1;

    private final AdjustmentRepository repository;
    private final WorkContext context;

    @Inject
    public AdjustmentListViewModel(AdjustmentRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
        load();
    }

    public boolean canApprove() {
        return context.canApproveAdjustments();
    }

    @Override
    protected void fetch(RepositoryCallback<List<Adjustment>> callback) {
        repository.getAdjustments(callback);
    }

    @Override
    protected boolean belongsToTab(Adjustment a, int tab) {
        if (tab == TAB_MINE) return a.getCreatedById().equals(context.userId());
        if (tab == TAB_PENDING) return a.getStatus() == AdjustmentStatus.PENDIENTE;
        return true;
    }

    @Override
    protected TaskSummary toSummary(Adjustment a) {
        TaskSummary.Badge badge;
        switch (a.getStatus()) {
            case APROBADO: badge = TaskSummary.Badge.SUCCESS; break;
            case RECHAZADO: badge = TaskSummary.Badge.DANGER; break;
            default: badge = TaskSummary.Badge.WARNING;
        }
        return new TaskSummary(a.getId(),
                a.signedQuantity() + " · " + a.getProductName(),
                a.getReason().getLabel() + " · " + a.getProductCode(),
                a.getCreatedByName() + " · " + formatDate(a),
                a.getStatus().getLabel(), badge);
    }

    @Override
    protected String idOf(Adjustment item) {
        return item.getId();
    }

    public void approve(Adjustment a) {
        repository.approve(a.getId(), this::onReviewResult);
    }

    public void reject(Adjustment a, String note) {
        repository.reject(a.getId(), note, this::onReviewResult);
    }

    private void onReviewResult(OperationResult result) {
        showMessage(result.getMessage());
        if (result.isSuccess()) load();
    }

    public void seedDemoData() {
        repository.seedDemoData(reloadAfter("Ajustes de prueba cargados.", "No se pudieron cargar los datos."));
    }

    static String formatDate(Adjustment a) {
        return a.getCreatedAt() == null ? "recién creado"
                : new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(a.getCreatedAt());
    }
}
