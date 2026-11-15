package com.example.erprukainventario.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SerialsTest {

    @Test
    public void normalizaEspaciosYMayusculas() {
        assertEquals("SN12-AB", Serials.normalize("  sn 12-ab "));
    }

    @Test
    public void rechazaVaciosLargosYCaracteresRaros() {
        assertNull(Serials.normalize("   "));
        assertNull(Serials.normalize("ABC#1"));
        StringBuilder longSerial = new StringBuilder();
        for (int i = 0; i <= Serials.MAX_LENGTH; i++) longSerial.append('A');
        assertNull(Serials.normalize(longSerial.toString()));
    }

    @Test
    public void docIdNoLlevaBarras() {
        assertEquals("EMP_p1_SN-1", Serials.docId("EMP", "p1", "SN/1"));
    }

    @Test
    public void parseSeparaYQuitaRepetidos() {
        assertEquals(Arrays.asList("A1", "B2", "C3"), Serials.parse("a1, b2\nc3  a1"));
    }

    @Test
    public void validaDuplicadosCodigoDelProductoYListaPermitida() {
        assertNotNull(Serials.validateNew("A1", Collections.singletonList("A1"), null));
        assertNotNull(Serials.validateNew("7801234567896", null, null, "7801234567896", "PROD-107"));
        assertNotNull(Serials.validateNew("Z9", null, Arrays.asList("A1", "B2")));
        assertNull(Serials.validateNew("B2", Collections.singletonList("A1"), Arrays.asList("A1", "B2")));
        assertNotNull(Serials.validateNew(null, null, null));
    }

    @Test
    public void soloSeRecibeLoQueNoEstaEnLaEmpresa() {
        assertTrue(SerialStatus.canReceive(null));
        assertTrue(SerialStatus.canReceive(SerialStatus.DESPACHADO));
        assertTrue(SerialStatus.canReceive(SerialStatus.BAJA));
        assertFalse(SerialStatus.canReceive(SerialStatus.EN_BODEGA));
        assertFalse(SerialStatus.canReceive(SerialStatus.EN_TRANSITO));
    }
}
