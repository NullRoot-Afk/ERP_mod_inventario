package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.SerialRepository;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.domain.Serials;
import com.example.erprukainventario.reception.data.ReceptionRepository;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;
import com.example.erprukainventario.reception.domain.Discrepancy;
import com.example.erprukainventario.reception.domain.ReceiptEntry;
import com.example.erprukainventario.core.ui.Event;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionScanningViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionScanningUiState> uiState = new MutableLiveData<>(new ReceptionScanningUiState());

    private String currentOrderId;
    /** Lecturas confirmadas en esta sesión: al finalizar ingresan stock, lotes y ubicaciones. */
    private final List<ReceiptEntry> receipts = new ArrayList<>();
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();

    private final SerialRepository serialRepository;
    /** Productos con número de serie, por SKU en mayúsculas (las líneas de la OC traen el SKU). */
    private final Map<String, Product> serializedByCode = new HashMap<>();

    @Inject
    public ReceptionScanningViewModel(ReceptionRepository repository, ProductRepository products,
                                      SerialRepository serialRepository) {
        this.repository = repository;
        this.serialRepository = serialRepository;
        products.getProducts(list -> {
            for (Product p : list) {
                if (p.isSerialized()) serializedByCode.put(p.getCode().toUpperCase(Locale.ROOT), p);
            }
        });
    }

    /** El producto de la línea controla número de serie (guía §10). */
    public boolean isSerialized(PurchaseOrderItem item) {
        return item != null && serializedByCode.containsKey(code(item));
    }

    private static String code(PurchaseOrderItem item) {
        return item.getProductCode() != null ? item.getProductCode().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * Recepción de un producto con número de serie: antes de sumar, se verifica que
     * ninguna serie esté ya en esta sesión ni registrada en la empresa.
     */
    public void addSerializedReceipt(PurchaseOrderItem item, String lot, Date expiry, String location,
                                     List<String> serials) {
        Product product = serializedByCode.get(code(item));
        if (product == null || serials == null || serials.isEmpty()) return;
        for (ReceiptEntry r : receipts) {
            if (!code(item).equalsIgnoreCase(r.getProductCode())) continue;
            for (String s : serials) {
                if (r.getSerials().contains(s)) {
                    message.setValue(new Event<>("La serie " + s + " ya se leyó en esta recepción."));
                    return;
                }
            }
        }
        Map<String, List<String>> byProduct = new HashMap<>();
        byProduct.put(product.getId(), serials);
        serialRepository.checkReceivable(byProduct, error -> {
            if (error != null) {
                message.setValue(new Event<>(error));
                return;
            }
            addReceipt(item, serials.size(), lot, expiry, location, serials);
            message.setValue(new Event<>("+" + serials.size() + " un. de " + item.getProductName()
                    + " con series " + Serials.join(serials)));
        });
    }

    public LiveData<ReceptionScanningUiState> getUiState() {
        return uiState;
    }

    public LiveData<Event<String>> getMessage() {
        return message;
    }

    public void loadOrderDetail(String orderId) {
        this.currentOrderId = orderId;
        ReceptionScanningUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withLoading());
        } else {
            uiState.setValue(new ReceptionScanningUiState().withLoading());
        }

        repository.getOrderDetail(orderId, orderDetail -> {
            ReceptionScanningUiState state = uiState.getValue();
            if (state != null) {
                uiState.setValue(state.withOrderDetail(orderDetail));
            } else {
                uiState.setValue(new ReceptionScanningUiState().withOrderDetail(orderDetail));
            }
        });
    }

    public PurchaseOrderItem findMatchingItem(String scannedBarcode) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || current.orderDetail.getItems() == null || scannedBarcode == null) {
            return null;
        }

        String target = scannedBarcode.trim();
        for (PurchaseOrderItem item : current.orderDetail.getItems()) {
            if (target.equalsIgnoreCase(item.getBarcode())
                    || target.equalsIgnoreCase(item.getProductCode())
                    || target.equalsIgnoreCase(item.getId())) {
                return item;
            }
        }
        return null;
    }

    /** Suma unidades recibidas y registra la lectura con su lote, vencimiento y ubicación. */
    public void addReceipt(PurchaseOrderItem item, int quantity, String lot, Date expiry, String location) {
        addReceipt(item, quantity, lot, expiry, location, null);
    }

    private void addReceipt(PurchaseOrderItem item, int quantity, String lot, Date expiry, String location,
                            List<String> serials) {
        if (item == null || quantity <= 0) return;
        receipts.add(new ReceiptEntry(item.getId(), item.getProductCode(), item.getProductName(),
                quantity, lot, expiry, location, serials));
        addQuantityToItem(item.getId(), quantity);
    }

    public void reportDiscrepancy(PurchaseOrderItem item, Discrepancy.Type type, int quantity, String notes,
                                  byte[] photo) {
        ReceptionScanningUiState current = uiState.getValue();
        String orderCode = current != null && current.orderDetail != null ? current.orderDetail.getCode() : "";
        repository.reportDiscrepancy(currentOrderId, orderCode, item, type, quantity, notes, photo, ok ->
                message.setValue(new Event<>(ok
                        ? "Discrepancia registrada" + (photo != null ? " con foto." : ".")
                        : "No se pudo registrar la discrepancia.")));
    }

    private void addQuantityToItem(String itemId, int quantityToAdd) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null) return;

        PurchaseOrderDetail oldDetail = current.orderDetail;
        List<PurchaseOrderItem> updatedItems = new ArrayList<>();

        for (PurchaseOrderItem item : oldDetail.getItems()) {
            if (item.getId().equals(itemId)) {
                int newQty = Math.max(0, item.getReceivedQuantity() + quantityToAdd);
                updatedItems.add(new PurchaseOrderItem(
                        item.getId(),
                        item.getProductCode(),
                        item.getProductName(),
                        item.getBarcode(),
                        item.getOrderedQuantity(),
                        newQty
                ));
            } else {
                updatedItems.add(item);
            }
        }

        PurchaseOrderDetail newDetail = new PurchaseOrderDetail(
                oldDetail.getId(),
                oldDetail.getCode(),
                oldDetail.getSupplierName(),
                oldDetail.getStatus(),
                oldDetail.getEtaLabel(),
                updatedItems
        );

        uiState.setValue(current.withOrderDetail(newDetail));
    }

    public void setTab(ReceptionScanningUiState.FilterTab tab) {
        ReceptionScanningUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withTab(tab));
        }
    }

    public List<PurchaseOrderItem> getFilteredItems() {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || current.orderDetail.getItems() == null) {
            return new ArrayList<>();
        }

        List<PurchaseOrderItem> result = new ArrayList<>();
        for (PurchaseOrderItem item : current.orderDetail.getItems()) {
            if (current.currentTab == ReceptionScanningUiState.FilterTab.SCANNED) {
                if (item.getReceivedQuantity() > 0) {
                    result.add(item);
                }
            } else if (current.currentTab == ReceptionScanningUiState.FilterTab.PENDING) {
                if (item.getReceivedQuantity() < item.getOrderedQuantity()) {
                    result.add(item);
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    public void finishReception() {
        ReceptionScanningUiState current = uiState.getValue();
        if (current == null || current.orderDetail == null || currentOrderId == null) return;

        uiState.setValue(current.withSaving());

        List<ReceiptEntry> toSave = new ArrayList<>(receipts);
        repository.finishReception(currentOrderId, current.orderDetail.getCode(), current.orderDetail.getItems(),
                toSave, success -> {
            ReceptionScanningUiState state = uiState.getValue();
            if (state != null) {
                if (success) {
                    receipts.clear();
                    uiState.setValue(state.withFinished());
                } else {
                    uiState.setValue(state.withError("Error al guardar la recepción en Firebase. Intenta nuevamente."));
                }
            }
        });
    }
}
