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
    public static final String LOTS = "LOTS";
    public static final String DISCREPANCIES = "DISCREPANCIES";
    /** Unidades con número de serie: dónde está cada una (guía §10). */
    public static final String SERIALS = "SERIALS";
    /** Fotos de evidencia (JPEG comprimido como Blob), separadas para no inflar otros documentos. */
    public static final String EVIDENCE = "EVIDENCE";
    /** Subcolección de líneas usada por PICKING_ORDERS, COUNT_TASKS y TRANSFERS. */
    public static final String ITEMS = "ITEMS";

    private FirestoreCollections() {
    }
}
