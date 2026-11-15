package com.example.erprukainventario.adjustment.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.adjustment.data.AdjustmentRepository;
import com.example.erprukainventario.adjustment.domain.AdjustmentReason;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.ui.Event;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AdjustmentFormViewModel extends ViewModel {

    private final AdjustmentRepository adjustments;
    private final ProductRepository products;
    private final WorkContext context;

    private final MutableLiveData<Product> product = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);

    @Inject
    public AdjustmentFormViewModel(AdjustmentRepository adjustments, ProductRepository products, WorkContext context) {
        this.adjustments = adjustments;
        this.products = products;
        this.context = context;
    }

    public LiveData<Product> getProduct() { return product; }
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
            if (found == null) messages.setValue(new Event<>("No existe un producto con el código " + code.trim() + "."));
        });
    }

    public void submit(AdjustmentReason reason, boolean outgoing, String quantityText, String notes) {
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

        busy.setValue(true);
        adjustments.create(p, sign * qty, reason, notes, ok -> {
            busy.setValue(false);
            if (ok) saved.setValue(true);
            else messages.setValue(new Event<>("No se pudo registrar el ajuste."));
        });
    }
}
