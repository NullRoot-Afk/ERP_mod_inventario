package com.example.erprukainventario.reception.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.databinding.ItemReceptionProductBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import java.util.ArrayList;
import java.util.List;

public class ReceptionItemAdapter extends RecyclerView.Adapter<ReceptionItemAdapter.ViewHolder> {

    private List<PurchaseOrderItem> items = new ArrayList<>();

    public void submitList(List<PurchaseOrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReceptionProductBinding binding = ItemReceptionProductBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemReceptionProductBinding binding;

        ViewHolder(ItemReceptionProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PurchaseOrderItem item) {
            binding.tvProductName.setText(item.getProductName());
            binding.tvProductMeta.setText("SKU: " + item.getProductCode() + " · EAN: " + item.getBarcode());
            binding.tvQuantity.setText(item.getReceivedQuantity() + " / " + item.getOrderedQuantity() + " un.");
        }
    }
}
