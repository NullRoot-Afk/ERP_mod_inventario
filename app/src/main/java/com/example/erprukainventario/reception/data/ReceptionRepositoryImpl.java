package com.example.erprukainventario.reception.data;

import android.os.Handler;
import android.os.Looper;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.data.SessionManager;
import com.example.erprukainventario.auth.data.TenantConfigLocalDataSource;
import com.example.erprukainventario.auth.domain.AuthUser;
import com.example.erprukainventario.auth.domain.Company;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Offline-first, mismo patrón que AuthRepositoryImpl:
 * 1. Intenta traer la lista online.
 * 2. Si responde OK, sobreescribe el cache local (por bodega).
 * 3. Si falla por conectividad, sirve lo último cacheado.
 */
@Singleton
public class ReceptionRepositoryImpl implements ReceptionRepository {

    private final ReceptionApi api;
    private final ReceptionLocalDataSource localDataSource;
    private final SessionManager sessionManager;
    private final TenantConfigLocalDataSource tenantConfig;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Inject
    public ReceptionRepositoryImpl(
            ReceptionApi api,
            ReceptionLocalDataSource localDataSource,
            SessionManager sessionManager,
            TenantConfigLocalDataSource tenantConfig
    ) {
        this.api = api;
        this.localDataSource = localDataSource;
        this.sessionManager = sessionManager;
        this.tenantConfig = tenantConfig;
    }

    @Override
    public void getOrders(RepositoryCallback<List<PurchaseOrder>> callback) {
        AuthUser user = sessionManager.getCurrentUser();
        Company company = tenantConfig.getConfiguredCompany();

        if (user == null || company == null) {
            callback.onResult(new ArrayList<>());
            return;
        }

        String warehouseId = user.getWarehouseId();

        api.getOrders(company.getId(), warehouseId).enqueue(new Callback<List<PurchaseOrderDto>>() {
            @Override
            public void onResponse(Call<List<PurchaseOrderDto>> call, Response<List<PurchaseOrderDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PurchaseOrder> orders = mapDtos(response.body());
                    dbExecutor.execute(() -> {
                        localDataSource.replaceOrdersForWarehouse(warehouseId, orders);
                        mainHandler.post(() -> callback.onResult(orders));
                    });
                } else {
                    fallbackToCache(warehouseId, callback);
                }
            }

            @Override
            public void onFailure(Call<List<PurchaseOrderDto>> call, Throwable t) {
                fallbackToCache(warehouseId, callback);
            }
        });
    }

    private void fallbackToCache(String warehouseId, RepositoryCallback<List<PurchaseOrder>> callback) {
        dbExecutor.execute(() -> {
            List<PurchaseOrder> cached = localDataSource.getCachedOrders(warehouseId);
            mainHandler.post(() -> callback.onResult(cached));
        });
    }

    private List<PurchaseOrder> mapDtos(List<PurchaseOrderDto> dtos) {
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrderDto dto : dtos) {
            result.add(new PurchaseOrder(
                    dto.id, dto.code, dto.supplierName, dto.lineCount,
                    PurchaseOrderStatus.valueOf(dto.status), dto.etaLabel
            ));
        }
        return result;
    }
}