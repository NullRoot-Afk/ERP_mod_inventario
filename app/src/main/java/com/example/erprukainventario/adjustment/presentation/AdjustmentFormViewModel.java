package com.example.erprukainventario.adjustment.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.adjustment.data.AdjustmentRepository;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.domain.Serials;
import com.example.erprukainventario.core.ui.Event;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AdjustmentFormViewModel extends ViewModel {

    private final AdjustmentRepository adjustments;
    private final ProductRepository products;
    private final WorkContext context;

    private final LotRepository lotRepository;

    private final MutableLiveData<Product> product = new MutableLiveData<>();
    /** Lotes activos con unidades del producto buscado (para indicar el lote afectado). */
    private final MutableLiveData<List<Lot>> lots = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);

    @Inject
    public AdjustmentFormViewModel(AdjustmentRepository adjustments, ProductRepository products, WorkContext context,
                                   LotRepository lotRepository) {
        this.adjustments = adjustments;
        this.products = products;
        this.context = context;
        this.lotRepository = lotRepository;
    }

    public LiveData<Product> getProduct() { return product; }
    public LiveData<List<Lot>> getLots() { return lots; }
    public LiveData<Boolean> isBusy() { return busy; }
    public LiveData<Event<String>> getMessages() { return messages; }
    public LiveData<Boolean> isSaved() { return saved; }

    public String warehouseId() {
        return context.warehouseId();
    }

    public void lookup(String code) {
        if (code == null || code.trim().isEmpty()) {
            messages.setValue(new Event<>("Ingresa o escanea un código."));
            return;
        }
        busy.setValue(true);
        products.findByCode(code, found -> {
            busy.setValue(false);
            product.setValue(found);
            lots.setValue(new ArrayList<>());
            if (found == null) {
                messages.setValue(new Event<>("No existe un producto con el código " + code.trim() + "."));
                return;
            }
            lotRepository.getLotsForProduct(found.getId(), all -> {
                Product current = product.getValue();
                if (current == null || !current.getId().equals(found.getId())) return; // ya buscó otro
                List<Lot> active = new ArrayList<>();
                for (Lot lot : all) {
                    if (!lot.isRetired() && lot.getQuantity() > 0) active.add(lot);
                }
                lots.setValue(active);
            });
        });
    }

    /**
     * @param lot         lote afectado, o null para que la rebaja se descuente por FEFO al aprobar.
     * @param serialsText series separadas por coma o salto de línea (productos con número de serie).
     */
    public void submit(AdjustmentReason reason, boolean outgoing, String quantityText, String notes, Lot lot,
                       String serialsText) {
        Product p = product.getValue();
        if (p == null) {
            messages.setValue(new Event<>("Primero busca el producto."));
            return;
        }
        if (reason == null) {
            messages.setValue(new Event<>("Selecciona un motivo."));
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(quantityText.trim());
        } catch (NumberFormatException e) {
            qty = 0;
        }
        if (qty <= 0) {
            messages.setValue(new Event<>("La cantidad debe ser mayor a 0."));
            return;
        }
        int sign = reason.getSign() != 0 ? reason.getSign() : (outgoing ? -1 : 1);
        if (sign < 0 && qty > p.stockIn(context.warehouseId())) {
            messages.setValue(new Event<>("No puedes rebajar más que el stock actual ("
                    + p.stockIn(context.warehouseId()) + " un.)."));
            return;
        }
        if (sign < 0 && lot != null && qty > lot.getQuantity()) {
            messages.setValue(new Event<>("El lote " + lot.getLotNumber() + " solo tiene "
                    + lot.getQuantity() + " un. Indica otro lote o déjalo sin indicar (FEFO)."));
            return;
        }

        List<String> serials = p.isSerialized() ? Serials.parse(serialsText) : new ArrayList<>();
        if (p.isSerialized() && serials.size() != qty) {
            messages.setValue(new Event<>("Indica una serie por unidad: " + serials.size() + " de " + qty + "."));
            return;
        }

        busy.setValue(true);
        adjustments.create(p, lot, sign * qty, reason, notes, serials, result -> {
            busy.setValue(false);
            if (result.isSuccess()) saved.setValue(true);
            else messages.setValue(new Event<>(result.getMessage()));
        });
    }
}
