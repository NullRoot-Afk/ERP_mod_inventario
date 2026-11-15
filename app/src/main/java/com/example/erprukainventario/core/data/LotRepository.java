package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.LotTake;
import com.example.erprukainventario.core.domain.OperationResult;

import java.util.Date;
import java.util.List;
import java.util.Map;

/** Lotes y vencimientos (guía §10). */
public interface LotRepository {

    /** Lotes del producto en la bodega del usuario, del que vence antes al que vence después. */
    void getLotsForProduct(String productId, RepositoryCallback<List<Lot>> callback);

    /** Lotes activos con unidades de la bodega del usuario (dashboard, alertas, FEFO). */
    void getActiveLots(RepositoryCallback<List<Lot>> callback);

    /**
     * Agrega el ingreso de un lote. El mismo lote del mismo producto en la misma
     * bodega suma cantidad en vez de duplicarse. Sin número de lote pero con
     * vencimiento, se genera un lote "S/L-ddMMyyyy".
     */
    void addLotReceipt(Writes writes, String productId, String productCode, String productName,
                       String lotNumber, Date expiryDate, int quantity, String reference);

    /**
     * Plan FEFO (primero en vencer, primero en salir) para una salida de stock:
     * {productId: cantidad} -> {productId: lotes de los que se descuenta}.
     * Lo que no alcanza a cubrirse con lotes sale como stock sin lote.
     */
    void planFefo(Map<String, Integer> quantities, RepositoryCallback<Map<String, List<LotTake>>> callback);

    /** Descuenta el plan de un producto y devuelve el texto de lotes para el movimiento ("L1×5, L2×3"). */
    String applyTakes(Writes writes, List<LotTake> takes);

    /**
     * Suma unidades a un lote existente (ajuste de entrada con lote indicado) y lo
     * reactiva si estaba agotado. Un lote retirado no recibe unidades: devuelve null.
     */
    String addToLot(Writes writes, Lot lot, int quantity);

    /**
     * Retira un lote vencido o por vencer: lo marca RETIRADO y crea un ajuste
     * PENDIENTE (motivo "Producto vencido") para que un supervisor descuente el stock.
     */
    void retire(Lot lot, RepositoryCallback<OperationResult> callback);
}
