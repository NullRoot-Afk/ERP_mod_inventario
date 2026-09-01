package com.example.erprukainventario.auth.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.auth.domain.LoginResult;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class AuthRepositoryImpl implements AuthRepository {

    private final AuthApi authApi;
    private final AuthLocalDataSource localDataSource;
    private final TenantConfigLocalDataSource tenantConfig;
    private final SessionManager sessionManager;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public AuthRepositoryImpl(
            AuthApi authApi,
            AuthLocalDataSource localDataSource,
            TenantConfigLocalDataSource tenantConfig,
            SessionManager sessionManager
    ) {
        this.authApi = authApi;
        this.localDataSource = localDataSource;
        this.tenantConfig = tenantConfig;
        this.sessionManager = sessionManager;
    }

    @Override
    public void validateAndSaveCompany(String companyCode, RepositoryCallback<CompanyValidationResult> callback) {
        authApi.validateCompany(companyCode).enqueue(new Callback<Company>() {
            @Override
            public void onResponse(Call<Company> call, Response<Company> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Company company = response.body();
                    // Guardar es una operación de disco simple (SharedPreferences),
                    // no necesita ir a dbExecutor como Room.
                    tenantConfig.saveCompany(company);
                    callback.onResult(CompanyValidationResult.success(company));
                } else if (response.code() == 404) {
                    callback.onResult(CompanyValidationResult.error("No encontramos una empresa con ese código."));
                } else {
                    callback.onResult(CompanyValidationResult.error(
                            "Error del servidor (" + response.code() + "). Intenta nuevamente."));
                }
            }

            @Override
            public void onFailure(Call<Company> call, Throwable t) {
                callback.onResult(CompanyValidationResult.error(
                        "No hay conexión. La configuración inicial requiere internet la primera vez."));
            }
        });
    }

    @Override
    public void login(String username, String password, RepositoryCallback<LoginResult> callback) {
        Company company = tenantConfig.getConfiguredCompany();
        if (company == null) {
            // No debería pasar nunca en la práctica: SetupActivity garantiza
            // que no se llega a LoginActivity sin empresa configurada.
            // Se deja como guarda explícita en vez de asumir silenciosamente.
            callback.onResult(LoginResult.error("El dispositivo no tiene una empresa configurada."));
            return;
        }

        AuthApi.LoginRequest request = new AuthApi.LoginRequest(username, password, company.getId());

        authApi.login(request).enqueue(new Callback<AuthApi.LoginResponse>() {
            @Override
            public void onResponse(Call<AuthApi.LoginResponse> call, Response<AuthApi.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthUser user = response.body().user;
                    dbExecutor.execute(() -> {
                        localDataSource.cacheCredentials(username, password, user);
                        sessionManager.setCurrentUser(user);
                        mainHandler.post(() -> callback.onResult(LoginResult.success(user, false)));
                    });
                } else if (response.code() == 401) {
                    callback.onResult(LoginResult.error("Usuario o contraseña incorrectos."));
                } else {
                    callback.onResult(LoginResult.error("Error del servidor (" + response.code() + "). Intenta nuevamente."));
                }
            }

            @Override
            public void onFailure(Call<AuthApi.LoginResponse> call, Throwable t) {
                if (!(t instanceof IOException)) {
                    callback.onResult(LoginResult.error("Ocurrió un error inesperado. Intenta nuevamente."));
                    return;
                }
                // Sin conexión: validar contra lo último sincronizado.
                // Ya no se pasa warehouseId: el usuario está cacheado por
                // username, y la bodega/empresa quedan fijas en el registro.
                dbExecutor.execute(() -> {
                    AuthUser cachedUser = localDataSource.validateOffline(username, password);
                    mainHandler.post(() -> {
                        if (cachedUser != null) {
                            sessionManager.setCurrentUser(cachedUser);
                            callback.onResult(LoginResult.success(cachedUser, true));
                        } else {
                            callback.onResult(LoginResult.error(
                                    "Sin conexión y no hay una sesión previa válida para este usuario."
                            ));
                        }
                    });
                });
            }
        });
    }
}