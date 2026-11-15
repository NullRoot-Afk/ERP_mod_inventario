package com.example.erprukainventario.core.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.domain.SerialNumber;
import com.example.erprukainventario.core.domain.SerialStatus;

import java.util.List;
import java.util.Map;

/** Trazabilidad por número de serie (guía §10). */
public interface SerialRepository {

    /** Series de un producto en todas las bodegas de la empresa, de la última movida a la más antigua. */
    void getSerialsForProduct(String productId, RepositoryCallback<List<SerialNumber>> callback);

    /** Busca una serie (ya normalizada) en todos los productos de la empresa. */
    void findBySerial(String serial, RepositoryCallback<List<SerialNumber>> callback);

    /**
     * Antes de recibir: {productId: series}. Entrega el error si alguna ya está en la
     * empresa (en bodega o en tránsito), o null si todas se pueden recibir. Sin red,
     * lo que no esté en el caché no se puede verificar: lo rechazarán las reglas al sincronizar.
     */
    void checkReceivable(Map<String, List<String>> serialsByProduct, RepositoryCallback<String> callback);

    /** Antes de sacar: entrega el error si alguna serie no está en la bodega del usuario, o null. */
    void checkInMyWarehouse(Map<String, List<String>> serialsByProduct, RepositoryCallback<String> callback);

    /** Alta (o reingreso) de series en la bodega del usuario. */
    void addReceived(Writes writes, String productId, String productCode, String productName,
                     List<String> serials, String reference);

    /**
     * Solo supervisor/jefe: registra las series de unidades que ya estaban en la bodega
     * antes de activar el control por serie. No cambia el stock.
     */
    void registerExisting(String productId, String productCode, String productName, List<String> serials,
                          RepositoryCallback<com.example.erprukainventario.core.domain.OperationResult> callback);

    /** Cambia el estado y la bodega de series existentes (despacho, envío, recepción de transferencia, baja). */
    void move(Writes writes, String productId, List<String> serials, SerialStatus status,
              String warehouseId, String reference);
}
