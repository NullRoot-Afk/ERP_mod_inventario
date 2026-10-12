package com.example.erprukainventario.auth.presentation;

import com.example.erprukainventario.auth.domain.AuthUser;

public class LoginUiState {

    public final String email;
    public final String password;
    public final boolean isLoading;
    public final String errorMessage;
    public final AuthUser loggedInUser;
    public final boolean loggedInFromCache;

    public LoginUiState() {
        this("", "", false, null, null, false);
    }

    private LoginUiState(String email, String password, boolean isLoading, String errorMessage,
                         AuthUser loggedInUser, boolean loggedInFromCache) {
        this.email = email;
        this.password = password;
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.loggedInUser = loggedInUser;
        this.loggedInFromCache = loggedInFromCache;
    }

    public boolean isSubmitEnabled() {
        return !email.trim().isEmpty() && !password.trim().isEmpty() && !isLoading;
    }

    public LoginUiState withEmail(String value) {
        return new LoginUiState(value, password, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withPassword(String value) {
        return new LoginUiState(email, value, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withLoading(boolean value) {
        return new LoginUiState(email, password, value, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withError(String message) {
        return new LoginUiState(email, password, false, message, null, false);
    }

    public LoginUiState withLoggedInUser(AuthUser user, boolean fromCache) {
        return new LoginUiState(email, password, false, null, user, fromCache);
    }
}
