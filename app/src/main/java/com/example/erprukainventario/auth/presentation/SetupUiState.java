package com.example.erprukainventario.auth.presentation;

public class SetupUiState {

    public final String companyCode;
    public final boolean isLoading;
    public final String errorMessage;
    public final boolean configured; // true cuando ya se guardó con éxito

    public SetupUiState() {
        this("", false, null, false);
    }

    private SetupUiState(String companyCode, boolean isLoading, String errorMessage, boolean configured) {
        this.companyCode = companyCode;
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.configured = configured;
    }

    public boolean isSubmitEnabled() {
        return !companyCode.trim().isEmpty() && !isLoading;
    }

    public SetupUiState withCompanyCode(String value) {
        return new SetupUiState(value, isLoading, null, configured);
    }

    public SetupUiState withLoading(boolean value) {
        return new SetupUiState(companyCode, value, null, configured);
    }

    public SetupUiState withError(String message) {
        return new SetupUiState(companyCode, false, message, false);
    }

    public SetupUiState withConfigured() {
        return new SetupUiState(companyCode, false, null, true);
    }
}
