package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.reception.data.ReceptionRepository;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionListViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionListUiState> uiState = new MutableLiveData<>(new ReceptionListUiState());

    private List<PurchaseOrder> masterOrders = new ArrayList<>();
    private String searchQuery = "";
    private boolean showOnlyPending = true;

    @Inject
    public ReceptionListViewModel(ReceptionRepository repository) {
        this.repository = repository;
        loadOrders();
    }

    public LiveData<ReceptionListUiState> getUiState() {
        return uiState;
    }

    public void loadOrders() {
        repository.getOrders(orders -> {
            this.masterOrders = orders != null ? orders : new ArrayList<>();
            applyFilters();
        });
    }

    /** Carga las 5 órdenes de prueba desde la app y refresca la lista. */
    public void seedDemoData(RepositoryCallback<Boolean> callback) {
        repository.seedDemoData(ok -> {
            if (ok) loadOrders();
            callback.onResult(ok);
        });
    }

    public void onSearchQueryChanged(String query) {
        this.searchQuery = query != null ? query.trim().toLowerCase() : "";
        applyFilters();
    }

    public void onTabSelected(boolean onlyPending) {
        this.showOnlyPending = onlyPending;
        applyFilters();
    }

    private void applyFilters() {
        List<PurchaseOrder> filtered = new ArrayList<>();
        for (PurchaseOrder order : masterOrders) {
            if (showOnlyPending && order.getStatus() == PurchaseOrderStatus.COMPLETA) {
                continue;
            }

            if (!searchQuery.isEmpty()) {
                boolean matchesCode = order.getCode() != null && order.getCode().toLowerCase().contains(searchQuery);
                boolean matchesSupplier = order.getSupplierName() != null && order.getSupplierName().toLowerCase().contains(searchQuery);
                if (!matchesCode && !matchesSupplier) {
                    continue;
                }
            }

            filtered.add(order);
        }

        ReceptionListUiState current = uiState.getValue();
        uiState.setValue((current != null ? current : new ReceptionListUiState())
                .withOrdersAndTab(filtered, showOnlyPending));
    }
}
