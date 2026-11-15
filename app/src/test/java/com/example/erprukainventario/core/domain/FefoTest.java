package com.example.erprukainventario.core.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class FefoTest {

    private static Date inDays(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, days);
        return c.getTime();
    }

    private static Lot lot(String id, String product, int days, int qty) {
        return new Lot(id, product, "C", "N", id, days == Integer.MAX_VALUE ? null : inDays(days),
                qty, "w", "", false);
    }

    @Test
    public void saleElQueVencePrimero() {
        List<Lot> lots = Arrays.asList(lot("L-TARDE", "p1", 60, 10), lot("L-PRONTO", "p1", 5, 10));
        List<LotTake> takes = Fefo.plan(lots, Collections.singletonMap("p1", 4)).get("p1");
        assertEquals(1, takes.size());
        assertEquals("L-PRONTO", takes.get(0).getLotId());
        assertEquals(4, takes.get(0).getQuantity());
        assertFalse(takes.get(0).depletes());
    }

    @Test
    public void cruzaVariosLotesYAgotaElPrimero() {
        List<Lot> lots = Arrays.asList(lot("A", "p1", 5, 3), lot("B", "p1", 20, 10));
        List<LotTake> takes = Fefo.plan(lots, Collections.singletonMap("p1", 7)).get("p1");
        assertEquals(2, takes.size());
        assertEquals("A", takes.get(0).getLotId());
        assertTrue(takes.get(0).depletes());
        assertEquals(4, takes.get(1).getQuantity());
    }

    @Test
    public void sinFechaVaAlFinalYElExcesoNoSeAsigna() {
        List<Lot> lots = Arrays.asList(lot("SIN", "p1", Integer.MAX_VALUE, 2), lot("CON", "p1", 10, 2));
        List<LotTake> takes = Fefo.plan(lots, Collections.singletonMap("p1", 9)).get("p1");
        assertEquals("CON", takes.get(0).getLotId());
        assertEquals("SIN", takes.get(1).getLotId());
        assertEquals(4, takes.get(0).getQuantity() + takes.get(1).getQuantity());
    }

    @Test
    public void ajusteConLoteIndicadoDescuentaDeEseLote() {
        List<Lot> lots = Arrays.asList(lot("PRONTO", "p1", 5, 10), lot("DANADO", "p1", 90, 6));
        List<LotTake> takes = Fefo.planFor(lots, "p1", 4, "DANADO");
        assertEquals(1, takes.size());
        assertEquals("DANADO", takes.get(0).getLotId());
        assertEquals(4, takes.get(0).getQuantity());
    }

    @Test
    public void ajusteConLoteIndicadoSinUnidadesSuficientesCompletaPorFefo() {
        List<Lot> lots = Arrays.asList(lot("TARDE", "p1", 60, 10), lot("PRONTO", "p1", 5, 10),
                lot("ELEGIDO", "p1", 90, 2));
        List<LotTake> takes = Fefo.planFor(lots, "p1", 5, "ELEGIDO");
        assertEquals(2, takes.size());
        assertEquals("ELEGIDO", takes.get(0).getLotId());
        assertTrue(takes.get(0).depletes());
        assertEquals("PRONTO", takes.get(1).getLotId());
        assertEquals(3, takes.get(1).getQuantity());
    }

    @Test
    public void ajusteSinLoteUsaFefo() {
        List<Lot> lots = Arrays.asList(lot("TARDE", "p1", 60, 10), lot("PRONTO", "p1", 5, 10));
        List<LotTake> takes = Fefo.planFor(lots, "p1", 3, null);
        assertEquals("PRONTO", takes.get(0).getLotId());
    }

    @Test
    public void loteRetiradoSeDescuentaPeroNoPasaAAgotado() {
        Lot retired = new Lot("RET", "p1", "C", "N", "RET", inDays(-2), 7, "w", "", true);
        List<LotTake> takes = Fefo.planFor(Arrays.asList(retired, lot("OK", "p1", 30, 5)), "p1", 7, "RET");
        assertEquals(1, takes.size());
        assertEquals(7, takes.get(0).getQuantity());
        assertFalse(takes.get(0).depletes());
        // Sin indicarlo, FEFO nunca toma un lote retirado.
        assertEquals("OK", Fefo.planFor(Arrays.asList(retired, lot("OK", "p1", 30, 5)), "p1", 2, null)
                .get(0).getLotId());
    }

    @Test
    public void ignoraOtrosProductosYProductosSinLote() {
        Map<String, List<LotTake>> plan = Fefo.plan(
                Collections.singletonList(lot("X", "p2", 5, 10)), Collections.singletonMap("p1", 3));
        assertTrue(plan.isEmpty());
    }
}
