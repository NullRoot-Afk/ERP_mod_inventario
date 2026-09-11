package com.example.erprukainventario.reception.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ReceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertOrders(List<PurchaseOrderEntity> orders);

    @Query("DELETE FROM cached_purchase_orders WHERE warehouseId = :warehouseId")
    void clearForWarehouse(String warehouseId);

    @Query("SELECT * FROM cached_purchase_orders WHERE warehouseId = :warehouseId ORDER BY code ASC")
    List<PurchaseOrderEntity> getOrders(String warehouseId);
}