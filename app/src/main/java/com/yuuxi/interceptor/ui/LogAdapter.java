package com.yuuxi.interceptor.ui;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yuuxi.interceptor.R;
import com.yuuxi.interceptor.logger.MethodCallLogger;
import com.yuuxi.interceptor.model.HookCallEntry;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<HookCallEntry> entries = new ArrayList<>();
    private int currentFilter = -1; // -1 = all

    // Colors for each type
    private static final int COLOR_JAVA_CALL = Color.parseColor("#00D4FF");
    private static final int COLOR_JAVA_RETURN = Color.parseColor("#00CC88");
    private static final int COLOR_NATIVE_CALL = Color.parseColor("#FF00FF");
    private static final int COLOR_NATIVE_RETURN = Color.parseColor("#CC66FF");
    private static final int COLOR_NATIVE_LOAD = Color.parseColor("#FFAA00");
    private static final int COLOR_SYSTEM = Color.parseColor("#AABBCC");
    private static final int COLOR_ERROR = Color.parseColor("#FF4444");

    public void setFilter(int filterType) {
        this.currentFilter = filterType;
        notifyDataSetChanged();
    }

    public void addEntry(HookCallEntry entry) {
        if (currentFilter >= 0 && entry.type != getFilteredType(currentFilter)) {
            return;
        }
        entries.add(entry);
        notifyItemInserted(entries.size() - 1);
    }

    public void addEntries(List<HookCallEntry> newEntries) {
        int startSize = entries.size();
        for (HookCallEntry entry : newEntries) {
            if (currentFilter >= 0 && entry.type != getFilteredType(currentFilter)) {
                continue;
            }
            entries.add(entry);
        }
        if (entries.size() > startSize) {
            notifyItemRangeInserted(startSize, entries.size() - startSize);
        }
    }

    public void clear() {
        int size = entries.size();
        entries.clear();
        notifyItemRangeRemoved(0, size);
    }

    public int getUnfilteredSize() {
        return entries.size();
    }

    private HookCallEntry.Type getFilteredType(int filterPosition) {
        switch (filterPosition) {
            case 0: return HookCallEntry.Type.JAVA_CALL;
            case 1: return HookCallEntry.Type.JAVA_RETURN;
            case 2: return HookCallEntry.Type.NATIVE_CALL;
            case 3: return HookCallEntry.Type.NATIVE_RETURN;
            case 4: return HookCallEntry.Type.SYSTEM;
            case 5: return HookCallEntry.Type.ERROR;
            default: return null;
        }
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        HookCallEntry entry = entries.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final View viewTypeDot;
        private final TextView tvTimestamp;
        private final TextView tvTypeBadge;
        private final TextView tvElapsed;
        private final TextView tvErrorBadge;
        private final TextView tvTitle;
        private final TextView tvDetail;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            viewTypeDot = itemView.findViewById(R.id.view_type_dot);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvTypeBadge = itemView.findViewById(R.id.tv_type_badge);
            tvElapsed = itemView.findViewById(R.id.tv_elapsed);
            tvErrorBadge = itemView.findViewById(R.id.tv_error_badge);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDetail = itemView.findViewById(R.id.tv_detail);
        }

        void bind(HookCallEntry entry) {
            // Timestamp
            tvTimestamp.setText(MethodCallLogger.formatTimestamp(entry.timestamp));

            // Type-specific formatting
            switch (entry.type) {
                case JAVA_CALL:
                    setColors(COLOR_JAVA_CALL);
                    tvTypeBadge.setText("JAVA");
                    tvTypeBadge.setBackgroundColor(COLOR_JAVA_CALL);
                    tvTitle.setText("↳ [JAVA] " + simpleClassName(entry.className) + "→" + entry.methodName + "()");
                    if (entry.args != null && !entry.args.isEmpty()) {
                        tvDetail.setVisibility(View.VISIBLE);
                        tvDetail.setText("  " + entry.args);
                    } else {
                        tvDetail.setVisibility(View.GONE);
                    }
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                case JAVA_RETURN:
                    if (entry.threw) {
                        setColors(COLOR_ERROR);
                        tvTypeBadge.setText("JAVA");
                        tvTypeBadge.setBackgroundColor(COLOR_JAVA_RETURN);
                        tvTitle.setText("↲ [JAVA] " + simpleClassName(entry.className) + "→" + entry.methodName);
                        tvErrorBadge.setVisibility(View.VISIBLE);
                        tvErrorBadge.setText("THREW");
                    } else {
                        setColors(COLOR_JAVA_RETURN);
                        tvTypeBadge.setText("JAVA");
                        tvTypeBadge.setBackgroundColor(COLOR_JAVA_RETURN);
                        tvTitle.setText("↲ [JAVA] " + simpleClassName(entry.className) + "→" + entry.methodName);
                        tvErrorBadge.setVisibility(View.GONE);
                    }
                    if (entry.result != null && !entry.result.isEmpty()) {
                        tvDetail.setVisibility(View.VISIBLE);
                        tvDetail.setText("  = " + entry.result);
                    } else {
                        tvDetail.setVisibility(View.GONE);
                    }
                    setElapsed(entry.elapsedUs);
                    break;

                case NATIVE_CALL:
                    setColors(COLOR_NATIVE_CALL);
                    tvTypeBadge.setText("NATIVE");
                    tvTypeBadge.setBackgroundColor(COLOR_NATIVE_CALL);
                    tvTitle.setText("↳ [NATIVE] " + entry.libName + "→" + entry.funcName + "()");
                    if (entry.address != 0) {
                        tvDetail.setVisibility(View.VISIBLE);
                        tvDetail.setText("  @0x" + Long.toHexString(entry.address));
                    } else {
                        tvDetail.setVisibility(View.GONE);
                    }
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                case NATIVE_RETURN:
                    setColors(COLOR_NATIVE_RETURN);
                    tvTypeBadge.setText("NATIVE");
                    tvTypeBadge.setBackgroundColor(COLOR_NATIVE_RETURN);
                    tvTitle.setText("↲ [NATIVE] " + entry.libName + "→" + entry.funcName);
                    tvDetail.setVisibility(View.VISIBLE);
                    tvDetail.setText("  = 0x" + Long.toHexString(entry.retval));
                    setElapsed(entry.elapsedUs);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                case NATIVE_LOAD:
                    setColors(COLOR_NATIVE_LOAD);
                    tvTypeBadge.setText("LOAD");
                    tvTypeBadge.setBackgroundColor(COLOR_NATIVE_LOAD);
                    tvTitle.setText("📦 [LOAD] " + entry.libName);
                    if (entry.message != null) {
                        tvDetail.setVisibility(View.VISIBLE);
                        tvDetail.setText("  " + entry.message);
                    } else {
                        tvDetail.setVisibility(View.GONE);
                    }
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                case SYSTEM:
                    setColors(COLOR_SYSTEM);
                    tvTypeBadge.setText("SYS");
                    tvTypeBadge.setBackgroundColor(COLOR_SYSTEM);
                    tvTitle.setText("ℹ [SYS] " + entry.message);
                    tvDetail.setVisibility(View.GONE);
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                case ERROR:
                    setColors(COLOR_ERROR);
                    tvTypeBadge.setText("ERR");
                    tvTypeBadge.setBackgroundColor(COLOR_ERROR);
                    tvTitle.setText("❌ [ERR] " + entry.message);
                    tvDetail.setVisibility(View.GONE);
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;

                default:
                    setColors(COLOR_SYSTEM);
                    tvTypeBadge.setText("?");
                    tvTypeBadge.setBackgroundColor(COLOR_SYSTEM);
                    tvTitle.setText("???");
                    tvDetail.setVisibility(View.GONE);
                    tvElapsed.setVisibility(View.GONE);
                    tvErrorBadge.setVisibility(View.GONE);
                    break;
            }
        }

        private void setColors(int color) {
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(color);
            viewTypeDot.setBackground(dotBg);
        }

        private void setElapsed(long elapsedUs) {
            if (elapsedUs > 0) {
                tvElapsed.setVisibility(View.VISIBLE);
                if (elapsedUs < 1000) {
                    tvElapsed.setText(elapsedUs + "us");
                } else if (elapsedUs < 1000000) {
                    tvElapsed.setText(String.format("%.1fms", elapsedUs / 1000.0));
                } else {
                    tvElapsed.setText(String.format("%.2fs", elapsedUs / 1000000.0));
                }
            } else {
                tvElapsed.setVisibility(View.GONE);
            }
        }

        private static String simpleClassName(String full) {
            if (full == null) return "?";
            int idx = full.lastIndexOf('.');
            return idx >= 0 ? full.substring(idx + 1) : full;
        }
    }
}
