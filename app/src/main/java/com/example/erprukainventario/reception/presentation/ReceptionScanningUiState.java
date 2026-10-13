package com.example.erprukainventario.reception.presentation;

import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;

public class ReceptionScanningUiState {

    public enum FilterTab { ALL, SCANNED, PENDING }

    public final PurchaseOrderDetail orderDetail;
    public final boolean isLoading;
    public final boolean isSaving;
    public final boolean isFinished;
    public final FilterTab currentTab;
    public final String errorMessage;

    public ReceptionScanningUiState() {
        this(null, true, false, false, FilterTab.ALL, null);
    }

    private ReceptionScanningUiState(
            PurchaseOrderDetail orderDetail,
            boolean isLoading,
            boolean isSaving,
            boolean isFinished,
            FilterTab currentTab,
            String errorMessage
    ) {
        this.orderDetail = orderDetail;
        this.isLoading = isLoading;
        this.isSaving = isSaving;
        this.isFinished = isFinished;
        this.currentTab = currentTab;
        this.errorMessage = errorMessage;
    }

    public ReceptionScanningUiState withLoading() {
        return new ReceptionScanningUiState(orderDetail, true, false, false, currentTab, null);
    }

    public ReceptionScanningUiState withOrderDetail(PurchaseOrderDetail orderDetail) {
        return new ReceptionScanningUiState(orderDetail, false, false, false, currentTab, null);
    }

    public ReceptionScanningUiState withTab(FilterTab tab) {
        return new ReceptionScanningUiState(orderDetail, isLoading, isSaving, isFinished, tab, errorMessage);
    }

    public ReceptionScanningUiState withSaving() {
        return new ReceptionScanningUiState(orderDetail, false, true, false, currentTab, null);
    }

    public ReceptionScanningUiState withFinished() {
        return new ReceptionScanningUiState(orderDetail, false, false, true, currentTab, null);
    }

    public ReceptionScanningUiState withError(String message) {
        return new ReceptionScanningUiState(orderDetail, false, false, false, currentTab, message);
    }
}
