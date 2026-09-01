package com.example.erprukainventario.auth.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.AuthRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SetupViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<SetupUiState> uiState = new MutableLiveData<>(new SetupUiState());

    @Inject
    public SetupViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<SetupUiState> getUiState() {
        return uiState;
    }

    private SetupUiState current() {
        SetupUiState value = uiState.getValue();
        return value != null ? value : new SetupUiState();
    }

    public void onCompanyCodeChanged(String value) {
        uiState.setValue(current().withCompanyCode(value));
    }

    public void onConfirmClicked() {
        SetupUiState state = current();
        uiState.setValue(state.withLoading(true));

        authRepository.validateAndSaveCompany(state.companyCode.trim(), result -> {
            if (result.isSuccess()) {
                uiState.setValue(current().withConfigured());
            } else {
                uiState.setValue(current().withError(result.getErrorMessage()));
            }
        });
    }
}