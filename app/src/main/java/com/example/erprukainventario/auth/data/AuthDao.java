package com.example.erprukainventario.auth.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AuthDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertCredential(CachedCredentialEntity entity);

    @Query("SELECT * FROM cached_credentials WHERE username = :username AND warehouseId = :warehouseId LIMIT 1")
    CachedCredentialEntity findCredential(String username, String warehouseId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertWarehouses(List<CachedWarehouseEntity> entities);

    @Query("SELECT * FROM cached_warehouses ORDER BY name ASC")
    List<CachedWarehouseEntity> getWarehouses();
}
