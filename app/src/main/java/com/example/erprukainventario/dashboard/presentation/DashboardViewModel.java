package com.example.erprukainventario.dashboard.presentation;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.erprukainventario.adjustment.data.AdjustmentRepository;
import com.example.erprukainventario.adjustment.domain.Adjustment;
import com.example.erprukainventario.adjustment.domain.AdjustmentStatus;
import com.example.erprukainventario.auth.data.RepositoryCallback;
import com.example.erprukainventario.core.data.EvidenceRepository;
import com.example.erprukainventario.core.data.LotRepository;
import com.example.erprukainventario.core.data.ProductRepository;
import com.example.erprukainventario.core.data.WorkContext;
import com.example.erprukainventario.core.domain.Lot;
import com.example.erprukainventario.core.domain.Movement;
import com.example.erprukainventario.core.domain.MovementType;
import com.example.erprukainventario.core.domain.Product;
import com.example.erprukainventario.core.ui.Event;
import com.example.erprukainventario.reception.data.ReceptionRepository;
import com.example.erprukainventario.reception.domain.Discrepancy;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * Dashboard y reportes (guía §12): stock actual, stock crítico, discrepancias
 * pendientes, lotes por vencer y productividad por operario.
 * Productividad y resolución de discrepancias solo para supervisor/jefe de bodega.
 */
@HiltViewModel
public class DashboardViewModel extends ViewModel {

    public static final int PRODUCTIVITY_DAYS = 7;

    /** Resumen de un operario en el período. */
    public static class Productivity {
        public final String userName;
        public int operations;
        public int units;

        Productivity(String userName) {
            this.userName = userName;
        }
    }

    /** Indicadores de la fila superior. */
    public static class Kpis {
        public final int totalUnits;
        public final int skuCount;
        public final int lowStock;
        public final int pendingAdjustments;
        public final int openDiscrepancies;
        public final int expiringLots;

        Kpis(int totalUnits, int skuCount, int lowStock, int pendingAdjustments,
             int openDiscrepancies, int expiringLots) {
            this.totalUnits = totalUnits;
            this.skuCount = skuCount;
            this.lowStock = lowStock;
            this.pendingAdjustments = pendingAdjustments;
            this.openDiscrepancies = openDiscrepancies;
            this.expiringLots = expiringLots;
        }
    }

    private final ProductRepository products;
    private final AdjustmentRepository adjustments;
    private final ReceptionRepository receptions;
    private final LotRepository lots;
    private final EvidenceRepository evidence;
    private final WorkContext context;

    private final MutableLiveData<Kpis> kpis = new MutableLiveData<>();
    private final MutableLiveData<List<Product>> lowStock = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Lot>> expiringLots = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Discrepancy>> discrepancies = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Productivity>> productivity = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Event<String>> message = new MutableLiveData<>();

    // Partes de los KPIs que llegan por separado (los callbacks llegan en el hilo principal).
    private int totalUnits, skuCount, lowCount, pendingAdj, openDisc, expiring;
    private int pending;
    private int refreshId;

    @Inject
    public DashboardViewModel(ProductRepository products, AdjustmentRepository adjustments,
                              ReceptionRepository receptions, LotRepository lots,
                              EvidenceRepository evidence, WorkContext context) {
        this.products = products;
        this.adjustments = adjustments;
        this.receptions = receptions;
        this.lots = lots;
        this.evidence = evidence;
        this.context = context;
    }

    public LiveData<Kpis> getKpis() { return kpis; }
    public LiveData<List<Product>> getLowStock() { return lowStock; }
    public LiveData<List<Lot>> getExpiringLots() { return expiringLots; }
    public LiveData<List<Discrepancy>> getDiscrepancies() { return discrepancies; }
    public LiveData<List<Productivity>> getProductivity() { return productivity; }
    public LiveData<Boolean> isLoading() { return loading; }
    public LiveData<Event<String>> getMessage() { return message; }

    public boolean isSupervisor() {
        return context.canApproveAdjustments();
    }

    public String warehouseId() {
        return context.warehouseId();
    }

    public String warehouseName() {
        return context.warehouseName();
    }

    public void refresh() {
        final int id = ++refreshId;
        boolean supervisor = isSupervisor();
        pending = supervisor ? 5 : 4;
        loading.setValue(true);
        String warehouse = context.warehouseId();

        products.getProducts(list -> {
            if (id != refreshId) return;
            int units = 0;
            List<Product> low = new ArrayList<>();
            for (Product p : list) {
                units += p.stockIn(warehouse);
                if (p.isLowStockIn(warehouse)) low.add(p);
            }
            // Lo más grave primero: menor stock relativo al mínimo.
            Collections.sort(low, (a, b) -> Double.compare(
                    ratio(a, warehouse), ratio(b, warehouse)));
            totalUnits = units;
            skuCount = list.size();
            lowCount = low.size();
            lowStock.setValue(low);
            done(id);
        });

        adjustments.getAdjustments(list -> {
            if (id != refreshId) return;
            int n = 0;
            for (Adjustment a : list) if (a.getStatus() == AdjustmentStatus.PENDIENTE) n++;
            pendingAdj = n;
            done(id);
        });

        receptions.getOpenDiscrepancies(list -> {
            if (id != refreshId) return;
            openDisc = list.size();
            discrepancies.setValue(list);
            done(id);
        });

        lots.getActiveLots(list -> {
            if (id != refreshId) return;
            List<Lot> alert = new ArrayList<>();
            for (Lot l : list) if (l.needsAlert()) alert.add(l);
            expiring = alert.size();
            expiringLots.setValue(alert);
            done(id);
        });

        if (supervisor) {
            Calendar since = Calendar.getInstance();
            since.add(Calendar.DAY_OF_YEAR, -PRODUCTIVITY_DAYS);
            products.getWarehouseMovements(since.getTime(), list -> {
                if (id != refreshId) return;
                productivity.setValue(summarize(list));
                done(id);
            });
        }
    }

    private static double ratio(Product p, String warehouse) {
        return p.getMinStock() <= 0 ? Double.MAX_VALUE : (double) p.stockIn(warehouse) / p.getMinStock();
    }

    private static List<Productivity> summarize(List<Movement> movements) {
        Map<String, Productivity> byUser = new LinkedHashMap<>();
        for (Movement m : movements) {
            String key = !m.getUserId().isEmpty() ? m.getUserId() : m.getUserName();
            Productivity p = byUser.get(key);
            if (p == null) {
                p = new Productivity(m.getUserName().isEmpty() ? "Sin nombre" : m.getUserName());
                byUser.put(key, p);
            }
            p.operations++;
            if (m.getType() != MovementType.REUBICACION) p.units += Math.abs(m.getQuantity());
        }
        List<Productivity> result = new ArrayList<>(byUser.values());
        Collections.sort(result, (a, b) -> Integer.compare(b.operations, a.operations));
        return result;
    }

    private void done(int id) {
        if (id != refreshId) return;
        pending--;
        kpis.setValue(new Kpis(totalUnits, skuCount, lowCount, pendingAdj, openDisc, expiring));
        if (pending <= 0) loading.setValue(false);
    }

    public void retireLot(Lot lot) {
        lots.retire(lot, result -> {
            message.setValue(new Event<>(result.getMessage()));
            if (result.isSuccess()) refresh();
        });
    }

    public void resolveDiscrepancy(Discrepancy d) {
        receptions.resolveDiscrepancy(d.getId(), ok -> {
            message.setValue(new Event<>(ok ? "Discrepancia marcada como resuelta." : "No se pudo actualizar."));
            if (ok) refresh();
        });
    }

    public void loadPhoto(String evidenceId, RepositoryCallback<Bitmap> callback) {
        evidence.getPhoto(evidenceId, callback);
    }
}
