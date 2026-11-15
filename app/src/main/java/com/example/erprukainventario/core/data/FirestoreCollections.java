package com.example.erprukainventario.core.data;

/** Nombres de colección compartidos entre módulos (un solo lugar para cambiarlos). */
public final class FirestoreCollections {

    public static final String PRODUCTS = "PRODUCTS";
    public static final String MOVEMENTS = "MOVEMENTS";
    public static final String WAREHOUSES = "WAREHOUSES";
    public static final String PICKING_ORDERS = "PICKING_ORDERS";
    public static final String COUNT_TASKS = "COUNT_TASKS";
    public static final String TRANSFERS = "TRANSFERS";
    public static final String ADJUSTMENTS = "ADJUSTMENTS";
    /** Subcolección de líneas usada por PICKING_ORDERS, COUNT_TASKS y TRANSFERS. */
    public static final String ITEMS = "ITEMS";

    private FirestoreCollections() {
    }
}
