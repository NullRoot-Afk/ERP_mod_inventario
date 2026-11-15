package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.reception.data.ReceptionRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ReceptionDetailViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<ReceptionDetailUiState> uiState = new MutableLiveData<>(new ReceptionDetailUiState());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleted = new MutableLiveData<>(false);

    private final WorkContext context;

    @Inject
    public ReceptionDetailViewModel(ReceptionRepository repository, WorkContext context) {
        this.repository = repository;
        this.context = context;
    }

    public boolean canDelete() {
        return context.canDeleteRecords();
    }

    public LiveData<ReceptionDetailUiState> getUiState() { return uiState; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<Boolean> getDeleted() { return deleted; }
    public void clearMessage() { message.setValue(null); }

    public void loadOrderDetail(String orderId) {
        ReceptionDetailUiState current = uiState.getValue();
        uiState.setValue((current != null ? current : new ReceptionDetailUiState()).withLoading());

        repository.getOrderDetail(orderId, orderDetail -> {
            ReceptionDetailUiState state = uiState.getValue();
            uiState.setValue((state != null ? state : new ReceptionDetailUiState()).withOrderDetail(orderDetail));
        });
    }

    public void deleteOrder(String orderId) {
        repository.deleteOrder(orderId, ok -> {
            if (ok) deleted.setValue(true);
            else message.setValue("No se pudo eliminar la orden. Intenta nuevamente.");
        });
    }

    /** itemId == null crea el producto; con itemId lo modifica. */
    public void saveItem(String orderId, String itemId, String code, String name, String barcode, String qtyText) {
        String n = name == null ? "" : name.trim();
        String c = code == null ? "" : code.trim();
        String b = barcode == null ? "" : barcode.trim();

        int qty;
        try {
            qty = Integer.parseInt(qtyText == null ? "" : qtyText.trim());
        } catch (NumberFormatException e) {
            qty = 0;
        }
        if (n.isEmpty()) { message.setValue("Ingresa el nombre del producto."); return; }
        if (qty <= 0) { message.setValue("La cantidad debe ser mayor a cero."); return; }

        repository.saveItem(orderId, itemId, c, n, b, qty, ok -> {
            if (ok) {
                message.setValue(itemId == null ? "Producto agregado." : "Producto actualizado.");
                loadOrderDetail(orderId); // la lista se refresca al instante
            } else {
                message.setValue("No se pudo guardar el producto. Intenta nuevamente.");
            }
        });
    }

    public void deleteItem(String orderId, String itemId) {
        repository.deleteItem(orderId, itemId, ok -> {
            if (ok) {
                message.setValue("Producto eliminado.");
                loadOrderDetail(orderId);
            } else {
                message.setValue("No se pudo eliminar el producto.");
            }
        });
    }
}
