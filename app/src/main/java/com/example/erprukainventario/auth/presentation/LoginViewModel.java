package com.example.erprukainventario.auth.presentation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.AuthRepository;
import com.example.erprukainventario.auth.domain.LoginResult;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(new LoginUiState());

    @Inject
    public LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
        loadWarehouses();
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    private LoginUiState current() {
        LoginUiState value = uiState.getValue();
        return value != null ? value : new LoginUiState();
    }

    private void loadWarehouses() {
        authRepository.getWarehouses(new com.example.erprukainventario.auth.data.RepositoryCallback<List<Warehouse>>() {
            @Override
            public void onResult(List<Warehouse> warehouses) {
                uiState.setValue(current().withWarehouses(warehouses));
            }
        });
    }

    public void onUsernameChanged(@NonNull String value) {
        uiState.setValue(current().withUsername(value));
    }

    public void onPasswordChanged(@NonNull String value) {
        uiState.setValue(current().withPassword(value));
    }

    public void onWarehouseSelected(@NonNull Warehouse warehouse) {
        uiState.setValue(current().withSelectedWarehouse(warehouse));
    }

    public void onLoginClicked() {
        LoginUiState state = current();
        if (state.selectedWarehouse == null) return;

        uiState.setValue(state.withLoading(true));

        authRepository.login(state.username, state.password, state.selectedWarehouse.getId(),
                new com.example.erprukainventario.auth.data.RepositoryCallback<LoginResult>() {
                    @Override
                    public void onResult(LoginResult result) {
                        if (result.isSuccess()) {
                            uiState.setValue(current().withLoggedInUser(result.getUser(), result.isFromCache()));
                        } else {
                            uiState.setValue(current().withError(result.getErrorMessage()));
                        }
                    }
                });
    }
}