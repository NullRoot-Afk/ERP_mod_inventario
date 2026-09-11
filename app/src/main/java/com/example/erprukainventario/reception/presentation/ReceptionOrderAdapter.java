package com.example.erprukainventario.reception.presentation;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.erprukainventario.R;
import com.example.erprukainventario.databinding.ItemReceptionOrderBinding;
import com.example.erprukainventario.reception.domain.PurchaseOrder;
import com.example.erprukainventario.reception.domain.PurchaseOrderStatus;

import java.util.ArrayList;
import java.util.List;

public class ReceptionOrderAdapter extends RecyclerView.Adapter<ReceptionOrderAdapter.ViewHolder> {

    public interface OnOrderClickListener {
        void onOrderClick(PurchaseOrder order);
    }

    private List<PurchaseOrder> orders = new ArrayList<>();
    private final OnOrderClickListener listener;

    public ReceptionOrderAdapter(OnOrderClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<PurchaseOrder> newOrders) {
        this.orders = newOrders;
        notifyDataSetChanged(); // lista chica; para listas grandes usar DiffUtil
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReceptionOrderBinding binding = ItemReceptionOrderBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(orders.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemReceptionOrderBinding binding;

        ViewHolder(ItemReceptionOrderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PurchaseOrder order, OnOrderClickListener listener) {
            binding.tvCode.setText(order.getCode());
            binding.tvSubtitle.setText(
                    order.getSupplierName() + " · " + order.getLineCount() + " líneas"
            );
            binding.tvMeta.setText(order.getEtaLabel());

            applyStatusBadge(order.getStatus());
            binding.getRoot().setOnClickListener(v -> listener.onOrderClick(order));
        }

        private void applyStatusBadge(PurchaseOrderStatus status) {
            int bgRes;
            int textColorRes;
            int cardBgRes;
            String label;

            switch (status) {
                case PARCIAL:
                    bgRes = R.drawable.bg_badge_warning;
                    textColorRes = R.color.text_warning;
                    cardBgRes = R.drawable.bg_card_warning;
                    label = "Parcial";
                    break;
                case COMPLETA:
                    bgRes = R.drawable.bg_badge_success;
                    textColorRes = R.color.text_success;
                    cardBgRes = R.drawable.bg_card_success;
                    label = "Completa";
                    break;
                case SIN_INICIAR:
                default:
                    bgRes = R.drawable.bg_badge_neutral;
                    textColorRes = R.color.text_secondary;
                    cardBgRes = R.drawable.bg_card_default;
                    label = "Sin iniciar";
                    break;
            }

            binding.tvStatusBadge.setText(label);
            binding.tvStatusBadge.setBackgroundResource(bgRes);
            binding.tvStatusBadge.setTextColor(
                    binding.getRoot().getContext().getColor(textColorRes)
            );
            binding.getRoot().setBackgroundResource(cardBgRes);
        }
    }
}