package com.example.erprukainventario.reception.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.reception.data.ReceptionRepository;
import com.example.erprukainventario.reception.domain.PurchaseOrderDetail;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** Formulario de orden: sirve para CREAR (orderId == null) y para MODIFICAR. */
@HiltViewModel
public class ReceptionFormViewModel extends ViewModel {

    private final ReceptionRepository repository;
    private final MutableLiveData<PurchaseOrderDetail> loaded = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saved = new MutableLiveData<>(false);

    @Inject
    public ReceptionFormViewModel(ReceptionRepository repository) {
        this.repository = repository;
    }

    public LiveData<PurchaseOrderDetail> getLoaded() { return loaded; }
    public LiveData<Boolean> getSaving() { return saving; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getSaved() { return saved; }

    /** Recupera la orden por su id para precargar el formulario. */
    public void load(String orderId) {
        repository.getOrderDetail(orderId, loaded::setValue);
    }

    public void clearError() { error.setValue(null); }

    public void save(String orderId, String code, String supplier, String eta, String notes) {
        String c = code == null ? "" : code.trim();
        String s = supplier == null ? "" : supplier.trim();
        String e = eta == null ? "" : eta.trim();
        String n = notes == null ? "" : notes.trim();

        // Las entradas vacías se rechazan antes de llegar a la base.
        if (c.isEmpty()) { error.setValue("Ingresa el código de la orden."); return; }
        if (s.isEmpty()) { error.setValue("Ingresa el nombre del proveedor."); return; }
        if (e.isEmpty()) e = "Pendiente";

        saving.setValue(true);
        if (orderId == null) {
            repository.createOrder(c, s, e, n, newId -> {
                saving.setValue(false);
                if (newId != null) saved.setValue(true);
                else error.setValue("No se pudo crear la orden. Intenta nuevamente.");
            });
        } else {
            repository.updateOrderInfo(orderId, c, s, e, n, ok -> {
                saving.setValue(false);
                if (ok) saved.setValue(true);
                else error.setValue("No se pudo actualizar la orden. Intenta nuevamente.");
            });
        }
    }
}
