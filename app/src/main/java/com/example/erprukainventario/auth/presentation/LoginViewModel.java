package com.example.erprukainventario.auth.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.data.AuthRepository;
import com.example.erprukainventario.auth.domain.LoginResult;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(new LoginUiState());

    @Inject
    public LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
        // Ya no hay loadWarehouses(): la bodega viene resuelta del backend
        // dentro de AuthUser, no se elige en esta pantalla.
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    private LoginUiState current() {
        LoginUiState value = uiState.getValue();
        return value != null ? value : new LoginUiState();
    }

    public void onUsernameChanged(String value) {
        uiState.setValue(current().withUsername(value));
    }

    public void onPasswordChanged(String value) {
        uiState.setValue(current().withPassword(value));
    }

    public void onLoginClicked() {
        LoginUiState state = current();
        uiState.setValue(state.withLoading(true));

        authRepository.login(state.username, state.password, result -> {
            if (result.isSuccess()) {
                uiState.setValue(current().withLoggedInUser(result.getUser(), result.isFromCache()));
            } else {
                uiState.setValue(current().withError(result.getErrorMessage()));
            }
        });
    }
}