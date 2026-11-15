package com.example.erprukainventario.core.domain;

import java.util.Collections;
import java.util.Map;

/** Producto del catálogo, con su stock separado por bodega. */
public class Product {

    private final String id;
    private final String code;
    private final String name;
    private final String barcode;
    private final String location;
    private final String category;
    private final int minStock;
    private final Map<String, Integer> stockByWarehouse;

    public Product(String id, String code, String name, String barcode, String location,
                   String category, int minStock, Map<String, Integer> stockByWarehouse) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.barcode = barcode;
        this.location = location;
        this.category = category;
        this.minStock = minStock;
        this.stockByWarehouse = stockByWarehouse != null ? stockByWarehouse : Collections.<String, Integer>emptyMap();
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getBarcode() { return barcode; }
    public String getLocation() { return location; }
    public String getCategory() { return category; }
    public int getMinStock() { return minStock; }
    public Map<String, Integer> getStockByWarehouse() { return stockByWarehouse; }

    public int stockIn(String warehouseId) {
        Integer qty = stockByWarehouse.get(warehouseId);
        return qty != null ? qty : 0;
    }

    public int totalStock() {
        int total = 0;
        for (Integer qty : stockByWarehouse.values()) total += qty;
        return total;
    }

    /** Stock crítico: en o bajo el mínimo configurado (guía §12, alertas de quiebre). */
    public boolean isLowStockIn(String warehouseId) {
        return minStock > 0 && stockIn(warehouseId) <= minStock;
    }
}
