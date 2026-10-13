package com.example.erprukainventario.auth.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.erprukainventario.auth.data.AuthRepository;


import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<LoginUiState> uiState = new MutableLiveData<>(new LoginUiState());

    @Inject
    public LoginViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<LoginUiState> getUiState() {
        return uiState;
    }

    private LoginUiState current() {
        LoginUiState value = uiState.getValue();
        return value != null ? value : new LoginUiState();
    }

    public void onEmailChanged(String value) {
        uiState.setValue(current().withEmail(value));
    }

    public void onPasswordChanged(String value) {
        uiState.setValue(current().withPassword(value));
    }

    public void onForgotPasswordClicked() {
        LoginUiState state = current();
        if (state.email == null || state.email.trim().isEmpty()) {
            uiState.setValue(state.withError("Ingresa tu correo en el campo superior para enviarte el enlace de recuperación."));
            return;
        }

        uiState.setValue(state.withLoading(true));
        authRepository.sendPasswordResetEmail(state.email.trim(), errorMessage -> {
            if (errorMessage == null) {
                uiState.setValue(current().withError("Te hemos enviado un correo con el enlace para restablecer tu contraseña. Revisa también la carpeta de Spam."));
            } else {
                uiState.setValue(current().withError(errorMessage));
            }
        });
    }

    public void onLoginClicked() {
        LoginUiState state = current();
        uiState.setValue(state.withLoading(true));

        authRepository.login(state.email, state.password, result -> {
            if (result.isSuccess()) {
                uiState.setValue(current().withLoggedInUser(result.getUser(), result.isFromCache()));
            } else {
                uiState.setValue(current().withError(result.getErrorMessage()));
            }
        });
    }
}
