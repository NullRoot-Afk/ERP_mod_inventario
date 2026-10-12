package com.example.erprukainventario.reception.presentation;

import com.example.erprukainventario.reception.domain.PurchaseOrder;

import java.util.Collections;
import java.util.List;

public class ReceptionListUiState {

    public final List<PurchaseOrder> orders;
    public final boolean isLoading;
    public final boolean showOnlyPending;

    public ReceptionListUiState() {
        this(Collections.emptyList(), true, true);
    }

    private ReceptionListUiState(List<PurchaseOrder> orders, boolean isLoading, boolean showOnlyPending) {
        this.orders = orders;
        this.isLoading = isLoading;
        this.showOnlyPending = showOnlyPending;
    }

    public ReceptionListUiState withLoading() {
        return new ReceptionListUiState(orders, true, showOnlyPending);
    }

    public ReceptionListUiState withOrdersAndTab(List<PurchaseOrder> orders, boolean showOnlyPending) {
        return new ReceptionListUiState(orders, false, showOnlyPending);
    }
}
