package com.example.erprukainventario.core.domain;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Lote recibido de un producto, con su fecha de vencimiento (guía §10). */
public class Lot {

    /** Días de anticipación con que se alerta un vencimiento. */
    public static final int ALERT_DAYS = 30;

    private final String id;
    private final String productId;
    private final String productCode;
    private final String productName;
    private final String lotNumber;
    private final Date expiryDate; // null = sin vencimiento
    private final int quantity;
    private final String warehouseId;
    private final String reference;
    private final boolean retired;

    public Lot(String id, String productId, String productCode, String productName, String lotNumber,
               Date expiryDate, int quantity, String warehouseId, String reference, boolean retired) {
        this.id = id;
        this.productId = productId;
        this.productCode = productCode;
        this.productName = productName;
        this.lotNumber = lotNumber;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
        this.warehouseId = warehouseId;
        this.reference = reference;
        this.retired = retired;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getLotNumber() { return lotNumber; }
    public Date getExpiryDate() { return expiryDate; }
    public int getQuantity() { return quantity; }
    public String getWarehouseId() { return warehouseId; }
    public String getReference() { return reference; }
    public boolean isRetired() { return retired; }

    /** Días hasta el vencimiento (negativo si ya venció); Integer.MAX_VALUE si no vence. */
    public int daysToExpiry() {
        if (expiryDate == null) return Integer.MAX_VALUE;
        long diff = startOfDay(expiryDate) - startOfDay(new Date());
        return (int) TimeUnit.MILLISECONDS.toDays(diff);
    }

    public boolean isExpired() {
        return daysToExpiry() < 0;
    }

    /** Activo y vencido o por vencer dentro de {@link #ALERT_DAYS}. */
    public boolean needsAlert() {
        return !retired && quantity > 0 && daysToExpiry() <= ALERT_DAYS;
    }

    private static long startOfDay(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}
