package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.reception.data.ReceptionRepository;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionScanningViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionScanningUiState> uiState = new MutableLiveData<>(new ReceptionScanningUiState());

    private String currentOrderId;

    @Inject
    public ReceptionScanningViewModel(ReceptionRepository repository) {
        this.repository = repository;
    }

    public LiveData<ReceptionScanningUiState> getUiState() {
        return uiState;
    }

    public void loadOrderDetail(String orderId) {
        this.currentOrderId = orderId;
        ReceptionScanningUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withLoading());
        } else {
            uiState.setValue(new ReceptionScanningUiState().withLoading());
        }

        repository.getOrderDetail(orderId, orderDetail -> {
            ReceptionScanningUiState state = uiState.getValue();
            if (state != null) {
                uiState.setValue(state.withOrderDetail(orderDetail));
            } else {
                uiState.setValue(new ReceptionScanningUiState().withOrderDetail(orderDetail));
            }
        });
    }

    public PurchaseOrderItem findMatchingItem(String scannedBarcode) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || current.orderDetail.getItems() == null || scannedBarcode == null) {
            return null;
        }

        String target = scannedBarcode.trim();
        for (PurchaseOrderItem item : current.orderDetail.getItems()) {
            if (target.equalsIgnoreCase(item.getBarcode())
                    || target.equalsIgnoreCase(item.getProductCode())
                    || target.equalsIgnoreCase(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public void addQuantityToItem(String itemId, int quantityToAdd) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null) return;

        PurchaseOrderDetail oldDetail = current.orderDetail;
        List<PurchaseOrderItem> updatedItems = new ArrayList<>();

        for (PurchaseOrderItem item : oldDetail.getItems()) {
            if (item.getId().equals(itemId)) {
                int newQty = Math.max(0, item.getReceivedQuantity() + quantityToAdd);
                updatedItems.add(new PurchaseOrderItem(
                        item.getId(),
                        item.getProductCode(),
                        item.getProductName(),
                        item.getBarcode(),
                        item.getOrderedQuantity(),
                        newQty
                ));
            } else {
                updatedItems.add(item);
            }
        }

        PurchaseOrderDetail newDetail = new PurchaseOrderDetail(
                oldDetail.getId(),
                oldDetail.getCode(),
                oldDetail.getSupplierName(),
                oldDetail.getStatus(),
                oldDetail.getEtaLabel(),
                updatedItems
        );

        uiState.setValue(current.withOrderDetail(newDetail));
    }

    public void setTab(ReceptionScanningUiState.FilterTab tab) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withTab(tab));
        }
    }

    public List<PurchaseOrderItem> getFilteredItems() {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || current.orderDetail.getItems() == null) {
            return new ArrayList<>();
        }

        List<PurchaseOrderItem> result = new ArrayList<>();
        for (PurchaseOrderItem item : current.orderDetail.getItems()) {
            if (current.currentTab == ReceptionScanningUiState.FilterTab.SCANNED) {
                if (item.getReceivedQuantity() > 0) {
                    result.add(item);
                }
            } else if (current.currentTab == ReceptionScanningUiState.FilterTab.PENDING) {
                if (item.getReceivedQuantity() < item.getOrderedQuantity()) {
                    result.add(item);
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    public void finishReception() {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || currentOrderId == null) return;

        uiState.setValue(current.withSaving());

        repository.updateReceptionProgress(currentOrderId, current.orderDetail.getItems(), success -> {
            ReceptionScanningUiState state = uiState.getValue();
            if (state != null) {
                if (success) {
                    uiState.setValue(state.withFinished());
                } else {
                    uiState.setValue(state.withError("Error al guardar la recepción en Firebase. Intenta nuevamente."));
                }
            }
        });
    }
}
