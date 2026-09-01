package com.example.erprukainventario.auth.data;

import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.auth.domain.LoginResult;

public interface AuthRepository {

    /** Configuración inicial del dispositivo (una sola vez). */
    void validateAndSaveCompany(String companyCode, RepositoryCallback<CompanyValidationResult> callback);

    void login(String username, String password, RepositoryCallback<LoginResult> callback);

    /** Resultado de validar un código de empresa contra el backend. */
    class CompanyValidationResult {
        private final boolean success;
        private final Company company;
        private final String errorMessage;

        private CompanyValidationResult(boolean success, Company company, String errorMessage) {
            this.success = success;
            this.company = company;
            this.errorMessage = errorMessage;
        }

        public static CompanyValidationResult success(Company company) {
            return new CompanyValidationResult(true, company, null);
        }

        public static CompanyValidationResult error(String message) {
            return new CompanyValidationResult(false, null, message);
        }

        public boolean isSuccess() { return success; }
        public Company getCompany() { return company; }
        public String getErrorMessage() { return errorMessage; }
    }
}

