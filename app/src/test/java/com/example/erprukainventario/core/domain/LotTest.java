package com.example.erprukainventario.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

public class LotTest {

    private static Date inDays(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTime();
    }

    private static Lot lot(Date expiry, int qty, boolean retired) {
        return new Lot("id", "p", "PROD-1", "Producto", "L1", expiry, qty, "w", "OC-1", retired);
    }

    @Test
    public void venceHoyEsCeroDias() {
        assertEquals(0, lot(new Date(), 5, false).daysToExpiry());
    }

    @Test
    public void alertaDentroDelPlazo() {
        assertTrue(lot(inDays(10), 5, false).needsAlert());
        assertTrue(lot(inDays(Lot.ALERT_DAYS), 5, false).needsAlert());
        assertFalse(lot(inDays(Lot.ALERT_DAYS + 1), 5, false).needsAlert());
    }

    @Test
    public void vencidoAlerta() {
        Lot expired = lot(inDays(-3), 5, false);
        assertTrue(expired.isExpired());
        assertEquals(-3, expired.daysToExpiry());
        assertTrue(expired.needsAlert());
    }

    @Test
    public void retiradoSinStockOSinFechaNoAlertan() {
        assertFalse(lot(inDays(-3), 5, true).needsAlert());
        assertFalse(lot(inDays(-3), 0, false).needsAlert());
        assertFalse(lot(null, 5, false).needsAlert());
    }
}
