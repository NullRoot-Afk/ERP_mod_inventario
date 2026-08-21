package com.example.erprukainventario.auth.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_warehouses")
public class CachedWarehouseEntity {

    @PrimaryKey
    public String id;

    public String name;
}
