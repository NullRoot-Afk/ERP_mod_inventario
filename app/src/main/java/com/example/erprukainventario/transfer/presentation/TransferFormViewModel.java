package com.example.erprukainventario.transfer.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.auth.domain.Warehouse;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.scan.ScanLine;
import com.example.erprukainventario.core.ui.Event;
import com.example.erprukainventario.transfer.data.TransferRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TransferFormViewModel extends ViewModel {

    public interface ProductFound { void onFound(Product product); }

    private final TransferRepository transfers;
    private final ProductRepository products;
    private final WorkContext context;

    private final MutableLiveData<List<Warehouse>> destinations = new MutableLiveData<>(new ArrayList<>());
    /** Borrador: scannedQuantity = cantidad a trasladar. */
    private final MutableLiveData<List<ScanLine>> lines = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> created = new MutableLiveData<>(false);

    @Inject
    public TransferFormViewModel(TransferRepository transfers, ProductRepository products, WorkContext context) {
        this.transfers = transfers;
        this.products = products;
        this.context = context;
        products.getWarehouses(list -> {
            List<Warehouse> others = new ArrayList<>();
            for (Warehouse w : list) if (!w.getId().equals(context.warehouseId())) others.add(w);
            destinations.setValue(others);
            if (others.isEmpty()) {
                messages.setValue(new Event<>("No hay otras bodegas registradas. Carga datos de prueba "
                        + "con toque largo en el título de Transferencias."));
            }
        });
    }

    public LiveData<List<Warehouse>> getDestinations() { return destinations; }
    public LiveData<List<ScanLine>> getLines() { return lines; }
    public LiveData<Boolean> isBusy() { return busy; }
    public LiveData<Event<String>> getMessages() { return messages; }
    public LiveData<Boolean> isCreated() { return created; }

    public String originName() { return context.warehouseName(); }
    public String originId() { return context.warehouseId(); }

    public void findProduct(String code, ProductFound onFound) {
        busy.setValue(true);
        products.findByCode(code, p -> {
            busy.setValue(false);
            if (p == null) messages.setValue(new Event<>("No existe un producto con el código " + code + "."));
            else onFound.onFound(p);
        });
    }

    /** Agrega o suma al producto; valida contra el stock disponible en la bodega de origen. */
    public void addProduct(Product p, int quantity) {
        List<ScanLine> current = new ArrayList<>(value());
        int existing = 0;
        int index = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getProductId().equals(p.getId())) {
                index = i;
                existing = current.get(i).getScannedQuantity();
            }
        }
        int total = existing + quantity;
        int available = p.stockIn(context.warehouseId());
        if (total > available) {
            messages.setValue(new Event<>("Stock insuficiente: hay " + available + " un. en tu bodega."));
            return;
        }
        ScanLine line = new ScanLine(p.getId(), p.getId(), p.getCode(), p.getName(), p.getBarcode(),
                p.getLocation(), available, total);
        if (index >= 0) current.set(index, line);
        else current.add(line);
        lines.setValue(current);
    }

    /** 0 elimina la línea. expectedQuantity guarda el stock disponible para validar. */
    public void updateLine(ScanLine line, int quantity) {
        if (quantity > line.getExpectedQuantity()) {
            messages.setValue(new Event<>("Stock insuficiente: hay " + line.getExpectedQuantity() + " un."));
            return;
        }
        List<ScanLine> current = new ArrayList<>();
        for (ScanLine l : value()) {
            if (!l.getId().equals(line.getId())) current.add(l);
            else if (quantity > 0) current.add(l.withScanned(quantity));
        }
        lines.setValue(current);
    }

    public void create(Warehouse destination, String notes) {
        if (destination == null) {
            messages.setValue(new Event<>("Elige la bodega de destino."));
            return;
        }
        if (value().isEmpty()) {
            messages.setValue(new Event<>("Agrega al menos un producto."));
            return;
        }
        busy.setValue(true);
        transfers.create(destination, notes, value(), result -> {
            busy.setValue(false);
            messages.setValue(new Event<>(result.getMessage()));
            if (result.isSuccess()) created.setValue(true);
        });
    }

    private List<ScanLine> value() {
        List<ScanLine> v = lines.getValue();
        return v != null ? v : new ArrayList<ScanLine>();
    }
}
