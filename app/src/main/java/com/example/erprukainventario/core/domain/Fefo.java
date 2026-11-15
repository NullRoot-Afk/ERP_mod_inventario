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

    /**
     * Salida de un solo producto que puede indicar el lote afectado (la caja dañada,
     * el lote retirado por vencimiento). Se descuenta primero de ese lote, aunque esté
     * retirado; lo que falte sale por FEFO del resto. Sin lote indicado, FEFO puro.
     *
     * @param preferredLotId id del lote indicado, o null.
     */
    public static List<LotTake> planFor(List<Lot> lots, String productId, int quantity, String preferredLotId) {
        List<LotTake> takes = new ArrayList<>();
        if (productId == null || quantity <= 0) return takes;
        int remaining = quantity;
        List<Lot> others = new ArrayList<>();
        for (Lot lot : lots) {
            if (preferredLotId == null || !lot.getId().equals(preferredLotId)) {
                others.add(lot);
                continue;
            }
            if (!lot.getProductId().equals(productId) || lot.getQuantity() <= 0) continue;
            int take = Math.min(remaining, lot.getQuantity());
            // Un lote retirado sigue RETIRADO aunque quede en cero (no pasa a AGOTADO).
            takes.add(new LotTake(lot.getId(), lot.getLotNumber(), take,
                    !lot.isRetired() && take == lot.getQuantity()));
            remaining -= take;
        }
        if (remaining > 0) {
            List<LotTake> rest = plan(others, Collections.singletonMap(productId, remaining)).get(productId);
            if (rest != null) takes.addAll(rest);
        }
        return takes;
    }
}
