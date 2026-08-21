package com.example.erprukainventario.auth.presentation;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.util.Collections;
import java.util.List;

/**
 * Todo lo que la Activity necesita para pintar la pantalla, en un solo
 * objeto inmutable. Evita LiveData sueltos y estados inconsistentes
 * (ej. loading=true y error visible al mismo tiempo).
 *
 * Se reconstruye con `withX(...)` en vez de mutarse, para que cada emisión
 * al LiveData sea un snapshot claro y comparable.
 */
public class LoginUiState {

    public final String username;
    public final String password;
    public final List<Warehouse> warehouses;
    public final Warehouse selectedWarehouse;
    public final boolean isLoading;
    public final String errorMessage;
    public final AuthUser loggedInUser;
    public final boolean loggedInFromCache;

    public LoginUiState() {
        this("", "", Collections.emptyList(), null, false, null, null, false);
    }

    private LoginUiState(String username, String password, List<Warehouse> warehouses,
                         Warehouse selectedWarehouse, boolean isLoading, String errorMessage,
                         AuthUser loggedInUser, boolean loggedInFromCache) {
        this.username = username;
        this.password = password;
        this.warehouses = warehouses;
        this.selectedWarehouse = selectedWarehouse;
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.loggedInUser = loggedInUser;
        this.loggedInFromCache = loggedInFromCache;
    }

    public boolean isSubmitEnabled() {
        return !username.trim().isEmpty()
                && !password.trim().isEmpty()
                && selectedWarehouse != null
                && !isLoading;
    }

    public LoginUiState withUsername(String value) {
        return new LoginUiState(value, password, warehouses, selectedWarehouse, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withPassword(String value) {
        return new LoginUiState(username, value, warehouses, selectedWarehouse, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withWarehouses(List<Warehouse> value) {
        return new LoginUiState(username, password, value, selectedWarehouse, isLoading, errorMessage, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withSelectedWarehouse(Warehouse value) {
        return new LoginUiState(username, password, warehouses, value, isLoading, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withLoading(boolean value) {
        return new LoginUiState(username, password, warehouses, selectedWarehouse, value, null, loggedInUser, loggedInFromCache);
    }

    public LoginUiState withError(String message) {
        return new LoginUiState(username, password, warehouses, selectedWarehouse, false, message, null, false);
    }

    public LoginUiState withLoggedInUser(AuthUser user, boolean fromCache) {
        return new LoginUiState(username, password, warehouses, selectedWarehouse, false, null, user, fromCache);
    }
}
