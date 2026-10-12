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
public class ReceptionProcessViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionProcessUiState> uiState = new MutableLiveData<>(new ReceptionProcessUiState());

    private String currentOrderId;

    @Inject
    public ReceptionProcessViewModel(ReceptionRepository repository) {
        this.repository = repository;
    }

    public LiveData<ReceptionProcessUiState> getUiState() {
        return uiState;
    }

    public void loadOrderDetail(String orderId) {
        this.currentOrderId = orderId;
        ReceptionProcessUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withLoading());
        } else {
            uiState.setValue(new ReceptionProcessUiState().withLoading());
        }

        repository.getOrderDetail(orderId, orderDetail -> {
            ReceptionProcessUiState state = uiState.getValue();
            if (state != null) {
                uiState.setValue(state.withOrderDetail(orderDetail));
            } else {
                uiState.setValue(new ReceptionProcessUiState().withOrderDetail(orderDetail));
            }
        });
    }

    public void updateItemQuantity(String itemId, int newReceivedQuantity) {
        ReceptionProcessUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null) return;

        PurchaseOrderDetail oldDetail = current.orderDetail;
        List<PurchaseOrderItem> updatedItems = new ArrayList<>();

        for (PurchaseOrderItem item : oldDetail.getItems()) {
            if (item.getId().equals(itemId)) {
                updatedItems.add(new PurchaseOrderItem(
                        item.getId(),
                        item.getProductCode(),
                        item.getProductName(),
                        item.getBarcode(),
                        item.getOrderedQuantity(),
                        newReceivedQuantity
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

    public void finishReception() {
        ReceptionProcessUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || currentOrderId == null) return;

        uiState.setValue(current.withSaving());

        repository.updateReceptionProgress(currentOrderId, current.orderDetail.getItems(), success -> {
            ReceptionProcessUiState state = uiState.getValue();
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
