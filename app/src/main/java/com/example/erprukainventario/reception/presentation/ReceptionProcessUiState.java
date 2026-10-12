package com.example.erprukainventario.reception.presentation;

import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;

public class ReceptionProcessUiState {

    public final PurchaseOrderDetail orderDetail;
    public final boolean isLoading;
    public final boolean isSaving;
    public final boolean isFinished;
    public final String errorMessage;

    public ReceptionProcessUiState() {
        this(null, true, false, false, null);
    }

    private ReceptionProcessUiState(
            PurchaseOrderDetail orderDetail,
            boolean isLoading,
            boolean isSaving,
            boolean isFinished,
            String errorMessage
    ) {
        this.orderDetail = orderDetail;
        this.isLoading = isLoading;
        this.isSaving = isSaving;
        this.isFinished = isFinished;
        this.errorMessage = errorMessage;
    }

    public ReceptionProcessUiState withLoading() {
        return new ReceptionProcessUiState(orderDetail, true, false, false, null);
    }

    public ReceptionProcessUiState withOrderDetail(PurchaseOrderDetail orderDetail) {
        return new ReceptionProcessUiState(orderDetail, false, false, false, null);
    }

    public ReceptionProcessUiState withSaving() {
        return new ReceptionProcessUiState(orderDetail, false, true, false, null);
    }

    public ReceptionProcessUiState withFinished() {
        return new ReceptionProcessUiState(orderDetail, false, false, true, null);
    }

    public ReceptionProcessUiState withError(String message) {
        return new ReceptionProcessUiState(orderDetail, false, false, false, message);
    }
}
