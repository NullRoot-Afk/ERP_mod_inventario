package com.example.erprukainventario.reception.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.databinding.ItemReceptionProcessProductBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import java.util.ArrayList;
import java.util.List;

public class ReceptionProcessAdapter extends RecyclerView.Adapter<ReceptionProcessAdapter.ViewHolder> {

    public interface OnQuantityChangeListener {
        void onQuantityChange(PurchaseOrderItem item, int newReceivedQuantity);
    }

    private List<PurchaseOrderItem> items = new ArrayList<>();
    private final OnQuantityChangeListener listener;

    public ReceptionProcessAdapter(OnQuantityChangeListener listener) {
        this.listener = listener;
    }

    public void submitList(List<PurchaseOrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReceptionProcessProductBinding binding = ItemReceptionProcessProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemReceptionProcessProductBinding binding;

        ViewHolder(ItemReceptionProcessProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PurchaseOrderItem item, OnQuantityChangeListener listener) {
            binding.tvProductName.setText(item.getProductName());
            binding.tvProductMeta.setText("SKU: " + item.getProductCode() + " · EAN: " + item.getBarcode());
            binding.tvCount.setText(item.getReceivedQuantity() + " / " + item.getOrderedQuantity() + " un.");

            binding.btnDecrement.setOnClickListener(v -> {
                if (item.getReceivedQuantity() > 0) {
                    listener.onQuantityChange(item, item.getReceivedQuantity() - 1);
                }
            });

            binding.btnIncrement.setOnClickListener(v -> {
                if (item.getReceivedQuantity() < item.getOrderedQuantity()) {
                    listener.onQuantityChange(item, item.getReceivedQuantity() + 1);
                }
            });
        }
    }
}
