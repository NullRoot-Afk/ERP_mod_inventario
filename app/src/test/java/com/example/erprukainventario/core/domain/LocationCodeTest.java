package com.example.erprukainventario.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationCodeTest {

    @Test
    public void normalizaFormatoCompleto() {
        assertEquals("A-03-02-B", LocationCode.normalize("A-03-02-B"));
    }

    @Test
    public void aceptaPrefijoMinusculasYUnDigito() {
        assertEquals("A-03-02-B", LocationCode.normalize("loc:a-3-2-b"));
        assertEquals("B-01-01", LocationCode.normalize(" b 1 1 "));
    }

    @Test
    public void posicionEsOpcional() {
        LocationCode code = LocationCode.parse("C-12-04");
        assertEquals("C", code.getZone());
        assertEquals("12", code.getAisle());
        assertEquals("04", code.getShelf());
        assertNull(code.getPosition());
        assertEquals("Zona C · Pasillo 12", code.aisleLabel());
    }

    @Test
    public void rechazaCodigosDeProducto() {
        assertFalse(LocationCode.isValid("7801234567890"));
        assertFalse(LocationCode.isValid("PROD-101"));
        assertFalse(LocationCode.isValid(""));
        assertFalse(LocationCode.isValid(null));
        assertTrue(LocationCode.isValid("AB-01-02-3"));
    }
}
