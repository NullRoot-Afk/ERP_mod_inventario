package com.example.erprukainventario.auth.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.LoginResult;
import com.example.erprukainventario.auth.domain.Warehouse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Offline-first:
 * 1. Intenta autenticar contra el backend (Retrofit, asíncrono).
 * 2. Si responde OK, cachea un hash de la contraseña en Room para permitir
 *    login offline en el futuro.
 * 3. Si la llamada falla por conectividad (IOException dentro del callback
 *    de Retrofit), valida contra el hash cacheado localmente.
 *
 * El trabajo de Room se hace en `dbExecutor` porque sus métodos son
 * síncronos; el resultado final siempre se entrega en el hilo principal
 * vía `mainHandler`, para que el ViewModel pueda actualizar LiveData
 * directamente sin volver a cambiar de hilo.
 */
@Singleton
public class AuthRepositoryImpl implements AuthRepository {

    private final AuthApi authApi;
    private final AuthLocalDataSource localDataSource;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public AuthRepositoryImpl(AuthApi authApi, AuthLocalDataSource localDataSource) {
        this.authApi = authApi;
        this.localDataSource = localDataSource;
    }

    @Override
    public void getWarehouses(RepositoryCallback<List<Warehouse>> callback) {
        authApi.getWarehouses().enqueue(new Callback<List<Warehouse>>() {
            @Override
            public void onResponse(Call<List<Warehouse>> call, Response<List<Warehouse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Warehouse> warehouses = response.body();
                    dbExecutor.execute(() -> {
                        localDataSource.saveWarehouses(warehouses);
                        mainHandler.post(() -> callback.onResult(warehouses));
                    });
                } else {
                    fallbackToCachedWarehouses(callback);
                }
            }

            @Override
            public void onFailure(Call<List<Warehouse>> call, Throwable t) {
                // Sin conexión (o el backend no respondió): usar lo cacheado.
                fallbackToCachedWarehouses(callback);
            }
        });
    }

    private void fallbackToCachedWarehouses(RepositoryCallback<List<Warehouse>> callback) {
        dbExecutor.execute(() -> {
            List<Warehouse> cached = localDataSource.getCachedWarehouses();
            mainHandler.post(() -> callback.onResult(cached));
        });
    }

    @Override
    public void login(String username, String password, String warehouseId, RepositoryCallback<LoginResult> callback) {
        AuthApi.LoginRequest request = new AuthApi.LoginRequest(username, password, warehouseId);

        authApi.login(request).enqueue(new Callback<AuthApi.LoginResponse>() {
            @Override
            public void onResponse(Call<AuthApi.LoginResponse> call, Response<AuthApi.LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthUser user = response.body().user;
                    dbExecutor.execute(() -> {
                        localDataSource.cacheCredentials(username, password, user);
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
                    // Error inesperado que no es de conectividad (ej. parseo).
                    callback.onResult(LoginResult.error("Ocurrió un error inesperado. Intenta nuevamente."));
                    return;
                }
                // Sin conexión: intentar validar contra lo último sincronizado.
                dbExecutor.execute(() -> {
                    AuthUser cachedUser = localDataSource.validateOffline(username, password, warehouseId);
                    mainHandler.post(() -> {
                        if (cachedUser != null) {
                            callback.onResult(LoginResult.success(cachedUser, true));
                        } else {
                            callback.onResult(LoginResult.error(
                                    "Sin conexión y no hay una sesión previa válida para este usuario en esta bodega."
                            ));
                        }
                    });
                });
            }
        });
    }
}
