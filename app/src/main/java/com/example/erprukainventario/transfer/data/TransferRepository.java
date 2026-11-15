package com.example.erprukainventario.transfer.data;

import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.domain.OperationResult;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.transfer.domain.Transfer;

import java.util.List;

public interface TransferRepository {

    /** Transferencias donde la bodega del usuario es origen o destino. */
    void getTransfers(RepositoryCallback<List<Transfer>> callback);

    /**
     * Solicita un traslado desde la bodega del usuario. Cada línea usa
     * scannedQuantity como cantidad solicitada.
     */
    void create(Warehouse destination, String notes, List<ScanLine> lines,
                RepositoryCallback<OperationResult> callback);

    void seedDemoData(RepositoryCallback<Boolean> callback);
}
