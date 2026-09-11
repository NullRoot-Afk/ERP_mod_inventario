package com.example.erprukainventario.reception.data;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ReceptionApi {

    /**
     * companyId y warehouseId se resuelven del lado del repositorio
     * (sesión actual + configuración del dispositivo), igual que hicimos
     * con companyId en el login — el ViewModel no necesita saber nada
     * de estos identificadores.
     */
    @GET("v1/reception/orders")
    Call<List<PurchaseOrderDto>> getOrders(
            @Query("companyId") String companyId,
            @Query("warehouseId") String warehouseId
    );
}

