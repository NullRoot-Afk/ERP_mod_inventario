package com.example.erprukainventario.reception.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ItemReceptionScanProductBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrderItem;

import java.util.ArrayList;
import java.util.List;

public class ReceptionScanningAdapter extends RecyclerView.Adapter<ReceptionScanningAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(PurchaseOrderItem item);
    }

    private List<PurchaseOrderItem> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public ReceptionScanningAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<PurchaseOrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReceptionScanProductBinding binding = ItemReceptionScanProductBinding.inflate(
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
        private final ItemReceptionScanProductBinding binding;

        ViewHolder(ItemReceptionScanProductBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PurchaseOrderItem item, OnItemClickListener listener) {
            binding.tvProductName.setText(item.getProductName());
            binding.tvProductMeta.setText("SKU: " + item.getProductCode() + " · EAN: " + item.getBarcode());
            binding.tvScanCount.setText(item.getReceivedQuantity() + " / " + item.getOrderedQuantity() + " un.");

            boolean isComplete = item.getReceivedQuantity() >= item.getOrderedQuantity();
            boolean isPartial = item.getReceivedQuantity() > 0 && !isComplete;

            if (isComplete) {
                binding.imgScanStatus.setImageResource(R.drawable.ic_check_circle_24);
                binding.imgScanStatus.setColorFilter(binding.getRoot().getContext().getColor(R.color.text_success));
                binding.tvScanCount.setTextColor(binding.getRoot().getContext().getColor(R.color.text_success));
            } else if (isPartial) {
                binding.imgScanStatus.setImageResource(R.drawable.ic_clock_24);
                binding.imgScanStatus.setColorFilter(binding.getRoot().getContext().getColor(R.color.text_warning));
                binding.tvScanCount.setTextColor(binding.getRoot().getContext().getColor(R.color.text_warning));
            } else {
                binding.imgScanStatus.setImageResource(R.drawable.ic_box_24);
                binding.imgScanStatus.setColorFilter(binding.getRoot().getContext().getColor(R.color.text_muted));
                binding.tvScanCount.setTextColor(binding.getRoot().getContext().getColor(R.color.text_secondary));
            }

            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}
