package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.OperationResult;
import com.google.firebase.firestore.WriteBatch;

import java.util.Date;
import java.util.List;

/** Lotes y vencimientos (guía §10). */
public interface LotRepository {

    /** Lotes del producto en la bodega del usuario, del que vence antes al que vence después. */
    void getLotsForProduct(String productId, RepositoryCallback<List<Lot>> callback);

    /** Lotes activos de la bodega del usuario (dashboard y alertas de vencimiento). */
    void getActiveLots(RepositoryCallback<List<Lot>> callback);

    /**
     * Agrega al batch el ingreso de un lote. El mismo lote del mismo producto en la
     * misma bodega suma cantidad en vez de duplicarse. Sin número de lote pero con
     * vencimiento, se genera un lote "S/L-ddMMyyyy".
     */
    void addLotReceipt(WriteBatch batch, String productId, String productCode, String productName,
                       String lotNumber, Date expiryDate, int quantity, String reference);

    /**
     * Retira un lote vencido o por vencer: lo marca RETIRADO y crea un ajuste
     * PENDIENTE (motivo "Producto vencido") para que un supervisor descuente el stock.
     */
    void retire(Lot lot, RepositoryCallback<OperationResult> callback);
}
