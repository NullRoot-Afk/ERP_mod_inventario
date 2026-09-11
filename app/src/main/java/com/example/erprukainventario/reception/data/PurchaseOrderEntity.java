package com.example.erprukainventario.reception.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cache local de las OC visibles para el operario. Se sobreescribe
 * completa cada vez que hay una respuesta online exitosa (ver
 * ReceptionRepositoryImpl) — es un cache de "última vista", no un
 * sistema de sincronización incremental.
 */
@Entity(tableName = "cached_purchase_orders")
public class PurchaseOrderEntity {

    @PrimaryKey
    @NonNull
    public String id;

    public String code;
    public String supplierName;
    public int lineCount;
    public String status;
    public String etaLabel;
    public String warehouseId; // para poder limpiar/filtrar por bodega si hiciera falta
}