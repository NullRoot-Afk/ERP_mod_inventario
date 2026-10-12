package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.reception.data.ReceptionRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionDetailViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionDetailUiState> uiState = new MutableLiveData<>(new ReceptionDetailUiState());

    @Inject
    public ReceptionDetailViewModel(ReceptionRepository repository) {
        this.repository = repository;
    }

    public LiveData<ReceptionDetailUiState> getUiState() {
        return uiState;
    }

    public void loadOrderDetail(String orderId) {
        ReceptionDetailUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withLoading());
        } else {
            uiState.setValue(new ReceptionDetailUiState().withLoading());
        }

        repository.getOrderDetail(orderId, orderDetail -> {
            ReceptionDetailUiState state = uiState.getValue();
            if (state != null) {
                uiState.setValue(state.withOrderDetail(orderDetail));
            } else {
                uiState.setValue(new ReceptionDetailUiState().withOrderDetail(orderDetail));
            }
        });
    }
}
