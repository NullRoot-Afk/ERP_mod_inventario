package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.reception.data.ReceptionRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionListViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionListUiState> uiState = new MutableLiveData<>(new ReceptionListUiState());

    @Inject
    public ReceptionListViewModel(ReceptionRepository repository) {
        this.repository = repository;
        loadOrders();
    }

    public LiveData<ReceptionListUiState> getUiState() {
        return uiState;
    }

    public void loadOrders() {
        uiState.setValue(uiState.getValue().withLoading());
        repository.getOrders(orders -> uiState.setValue(uiState.getValue().withOrders(orders)));
    }
}