package com.example.erprukainventario.auth.presentation;

import com.example.erprukainventario.auth.domain.AuthUser;

public class LoginUiState {

    public final String username;
    public final String password;
    public final boolean isLoading;
    public final String errorMessage;
    public final AuthUser loggedInUser;
    public final boolean loggedInFromCache;

    public LoginUiState() {
        this("", "", false, null, null, false);
    }

    private LoginUiState(String username, String password, boolean isLoading, String errorMessage,
                         AuthUser loggedInUser, boolean loggedInFromCache) {
        this.username = username;
        this.password = password;
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.loggedInUser = loggedInUser;
        this.loggedInFromCache = loggedInFromCache;
    }

    public boolean isSubmitEnabled() {
        return !username.trim().isEmpty() && !password.trim().isEmpty() && !isLoading;
    }

    public LoginUiState withUsername(String value) {
        return new LoginUiState(value, password, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withPassword(String value) {
        return new LoginUiState(username, value, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withLoading(boolean value) {
        return new LoginUiState(username, password, value, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withError(String message) {
        return new LoginUiState(username, password, false, message, null, false);
    }

    public LoginUiState withLoggedInUser(AuthUser user, boolean fromCache) {
        return new LoginUiState(username, password, false, null, user, fromCache);
    }
}
