package com.example.erprukainventario.reception.presentation;

import com.example.erprukainventario.reception.domain.PurchaseOrder;

import java.util.Collections;
import java.util.List;

public class ReceptionListUiState {

    public final List<PurchaseOrder> orders;
    public final boolean isLoading;

    public ReceptionListUiState() {
        this(Collections.emptyList(), true);
    }

    private ReceptionListUiState(List<PurchaseOrder> orders, boolean isLoading) {
        this.orders = orders;
        this.isLoading = isLoading;
    }

    public ReceptionListUiState withLoading() {
        return new ReceptionListUiState(orders, true);
    }

    public ReceptionListUiState withOrders(List<PurchaseOrder> orders) {
        return new ReceptionListUiState(orders, false);
    }
}