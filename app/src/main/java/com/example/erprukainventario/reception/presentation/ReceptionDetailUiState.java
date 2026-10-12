package com.example.erprukainventario.reception.presentation;

import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;

public class ReceptionDetailUiState {

    public final PurchaseOrderDetail orderDetail;
    public final boolean isLoading;
    public final String errorMessage;

    public ReceptionDetailUiState() {
        this(null, true, null);
    }

    private ReceptionDetailUiState(PurchaseOrderDetail orderDetail, boolean isLoading, String errorMessage) {
        this.orderDetail = orderDetail;
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
    }

    public ReceptionDetailUiState withLoading() {
        return new ReceptionDetailUiState(orderDetail, true, null);
    }

    public ReceptionDetailUiState withOrderDetail(PurchaseOrderDetail orderDetail) {
        return new ReceptionDetailUiState(orderDetail, false, null);
    }

    public ReceptionDetailUiState withError(String message) {
        return new ReceptionDetailUiState(orderDetail, false, message);
    }
}
