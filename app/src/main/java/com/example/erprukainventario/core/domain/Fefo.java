package com.example.erprukainventario.core.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** FEFO (First Expired, First Out): de qué lotes sale cada unidad (guía §10). */
public final class Fefo {

    private Fefo() {
    }

    /**
     * @param lots       lotes disponibles (de cualquier producto, en cualquier orden).
     * @param quantities {productId: unidades que salen}.
     * @return {productId: lotes a descontar}, del que vence antes al que vence después.
     *         Lo que exceda las unidades con lote no aparece (sale como stock sin lote).
     */
    public static Map<String, List<LotTake>> plan(List<Lot> lots, Map<String, Integer> quantities) {
        List<Lot> sorted = new ArrayList<>(lots);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.daysToExpiry(), b.daysToExpiry()));

        Map<String, List<LotTake>> plan = new HashMap<>();
        for (Map.Entry<String, Integer> e : quantities.entrySet()) {
            int remaining = e.getValue() != null ? e.getValue() : 0;
            List<LotTake> takes = new ArrayList<>();
            for (Lot lot : sorted) {
                if (remaining <= 0) break;
                if (lot.isRetired() || lot.getQuantity() <= 0 || !lot.getProductId().equals(e.getKey())) continue;
                int take = Math.min(remaining, lot.getQuantity());
                takes.add(new LotTake(lot.getId(), lot.getLotNumber(), take, take == lot.getQuantity()));
                remaining -= take;
            }
            if (!takes.isEmpty()) plan.put(e.getKey(), takes);
        }
        return plan;
    }
}
