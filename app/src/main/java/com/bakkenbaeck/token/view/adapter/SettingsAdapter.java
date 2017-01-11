package com.bakkenbaeck.token.view.adapter;


import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bakkenbaeck.token.R;
import com.bakkenbaeck.token.view.BaseApplication;
import com.bakkenbaeck.token.view.adapter.viewholder.ClickableViewHolder;

import java.util.ArrayList;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.ViewHolder> implements ClickableViewHolder.OnClickListener {

    private final ArrayList<String> settings;

    public SettingsAdapter() {
        this.settings = new ArrayList<>(3);
        this.settings.add("Security");
        this.settings.add("Local currency");
        this.settings.add("Sign out");
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__settings, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final String label = this.settings.get(position);
        holder.label.setText(label);
        holder.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return this.settings.size();
    }

    @Override
    public void onClick(final int position) {
        Toast.makeText(BaseApplication.get(), "These settings currently do nothing!", Toast.LENGTH_SHORT).show();
    }

    static class ViewHolder extends ClickableViewHolder {
        private TextView label;

        private ViewHolder(final View view) {
            super(view);
            this.label = (TextView) view.findViewById(R.id.label);
        }
    }
}