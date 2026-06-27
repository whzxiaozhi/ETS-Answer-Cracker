package com.ets100.reader;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.ViewHolder> {

    private static final int TYPE_GROUP_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items = new ArrayList<>();
    private int expandedPosition = -1;

    public void setItems(List<QuestionResult> newItems) {
        items.clear();
        expandedPosition = -1;

        // 按 groupId 分组，添加组标题
        int currentGroup = -1;
        int groupIndex = 0;
        for (QuestionResult qr : newItems) {
            int gid = qr.getGroupId();
            if (gid != currentGroup) {
                currentGroup = gid;
                groupIndex++;
                items.add(new GroupHeader(groupIndex, qr.getSourceFile()));
            }
            items.add(qr);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof GroupHeader ? TYPE_GROUP_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout layout = new LinearLayout(parent.getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (viewType == TYPE_GROUP_HEADER) {
            layout.setPadding(24, 20, 24, 12);
        } else {
            layout.setPadding(24, 12, 24, 12);
        }

        TextView tvTitle = new TextView(parent.getContext());
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(null, Typeface.BOLD);
        layout.addView(tvTitle);

        TextView tvResult = new TextView(parent.getContext());
        tvResult.setTextSize(15);
        tvResult.setPadding(0, 6, 0, 0);
        layout.addView(tvResult);

        TextView tvQuestion = new TextView(parent.getContext());
        tvQuestion.setTextSize(12);
        tvQuestion.setTextColor(Color.parseColor("#9E9E9E"));
        tvQuestion.setPadding(0, 4, 0, 0);
        tvQuestion.setVisibility(View.GONE);
        layout.addView(tvQuestion);

        return new ViewHolder(layout, tvTitle, tvResult, tvQuestion);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object obj = items.get(position);

        if (obj instanceof GroupHeader) {
            bindGroupHeader(holder, (GroupHeader) obj);
        } else {
            QuestionResult item = (QuestionResult) obj;
            boolean isExpanded = (position == expandedPosition);
            String type = item.getQuestionType();
            if (type == null) type = "";

            switch (type) {
                case "read":
                    bindRead(holder, item);
                    break;
                case "choose":
                    bindChoose(holder, item, isExpanded);
                    break;
                case "role":
                    bindRole(holder, item, isExpanded);
                    break;
                case "error":
                    bindError(holder, item);
                    break;
                default:
                    bindNoAnswer(holder, item);
                    break;
            }
        }
    }

    private void bindGroupHeader(ViewHolder holder, GroupHeader header) {
        holder.tvTitle.setText(header.displayName);
        holder.tvTitle.setTextColor(Color.parseColor("#1565C0"));
        holder.tvTitle.setTextSize(16);
        holder.tvResult.setText(header.sourceFile);
        holder.tvResult.setTextSize(11);
        holder.tvResult.setTextColor(Color.parseColor("#9E9E9E"));
        holder.tvQuestion.setVisibility(View.GONE);

        // 半透明浅蓝背景 + 圆角
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E3F2FD"));
        bg.setCornerRadius(24);
        holder.itemView.setBackground(bg);
        holder.itemView.setPadding(32, 16, 32, 16);
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        mlp.setMargins(0, 8, 0, 4);
        holder.itemView.setLayoutParams(mlp);
        holder.itemView.setOnClickListener(null);
    }

    private void bindRead(ViewHolder holder, QuestionResult item) {
        holder.tvTitle.setText("📖 阅读材料");
        holder.tvTitle.setTextColor(Color.parseColor("#2E7D32"));
        holder.tvTitle.setTextSize(13);
        holder.tvResult.setText(item.getContent() != null ? item.getContent() : "");
        holder.tvResult.setTextSize(14);
        holder.tvResult.setTextColor(Color.parseColor("#333333"));
        holder.tvQuestion.setVisibility(View.GONE);
        resetItemBg(holder);
    }

    private void bindChoose(ViewHolder holder, QuestionResult item, boolean isExpanded) {
        holder.tvTitle.setText("第" + item.getQuestionNumber() + "题");
        holder.tvTitle.setTextColor(Color.parseColor("#1565C0"));
        holder.tvTitle.setTextSize(14);

        String label = "[选择]";
        String answer = item.getAnswer() != null ? item.getAnswer() : "无";
        String result = label + " 答案: " + answer;
        SpannableString span = new SpannableString(result);
        span.setSpan(new ForegroundColorSpan(Color.parseColor("#2E7D32")), 0, label.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        holder.tvResult.setText(span);
        holder.tvResult.setTextSize(16);
        holder.tvResult.setTextColor(Color.BLACK);

        String qt = item.getQuestionText();
        if (qt != null && !qt.isEmpty()) {
            holder.tvQuestion.setText(qt);
            holder.tvQuestion.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        } else {
            holder.tvQuestion.setVisibility(View.GONE);
        }
        resetItemBg(holder);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int prev = expandedPosition;
            expandedPosition = (expandedPosition == pos) ? -1 : pos;
            if (prev != RecyclerView.NO_POSITION && prev >= 0) notifyItemChanged(prev);
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition);
        });
    }

    private void bindRole(ViewHolder holder, QuestionResult item, boolean isExpanded) {
        holder.tvTitle.setText("第" + item.getQuestionNumber() + "题");
        holder.tvTitle.setTextColor(Color.parseColor("#E65100"));
        holder.tvTitle.setTextSize(14);

        StringBuilder sb = new StringBuilder();
        String label = "[角色]";
        sb.append(label).append(" ");
        String first = item.getFirstValue();
        String second = item.getSecondLastValue();
        if (first != null && !first.isEmpty()) sb.append(first);
        if (second != null && !second.isEmpty()) {
            if (sb.length() > label.length() + 1) sb.append(" ... ");
            sb.append(second);
        }
        String result = sb.toString();
        SpannableString span = new SpannableString(result);
        span.setSpan(new ForegroundColorSpan(Color.parseColor("#E65100")), 0, label.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        holder.tvResult.setText(span);
        holder.tvResult.setTextSize(15);
        holder.tvResult.setTextColor(Color.BLACK);

        String qt = item.getQuestionText();
        if (qt != null && !qt.isEmpty()) {
            holder.tvQuestion.setText(qt);
            holder.tvQuestion.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        } else {
            holder.tvQuestion.setVisibility(View.GONE);
        }
        resetItemBg(holder);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int prev = expandedPosition;
            expandedPosition = (expandedPosition == pos) ? -1 : pos;
            if (prev != RecyclerView.NO_POSITION && prev >= 0) notifyItemChanged(prev);
            if (expandedPosition >= 0) notifyItemChanged(expandedPosition);
        });
    }

    private void bindNoAnswer(ViewHolder holder, QuestionResult item) {
        holder.tvTitle.setText("第" + item.getQuestionNumber() + "题");
        holder.tvTitle.setTextColor(Color.parseColor("#9E9E9E"));
        holder.tvResult.setText("无答案");
        holder.tvResult.setTextSize(15);
        holder.tvResult.setTextColor(Color.parseColor("#9E9E9E"));
        holder.tvQuestion.setVisibility(View.GONE);
        resetItemBg(holder);
        holder.itemView.setOnClickListener(null);
    }

    private void bindError(ViewHolder holder, QuestionResult item) {
        holder.tvTitle.setText("⚠️ 错误");
        holder.tvTitle.setTextColor(Color.parseColor("#E65100"));
        holder.tvResult.setText(item.getContent() != null ? item.getContent() : "未知错误");
        holder.tvResult.setTextSize(13);
        holder.tvResult.setTextColor(Color.parseColor("#E65100"));
        holder.tvQuestion.setVisibility(View.GONE);
        resetItemBg(holder);
        holder.itemView.setOnClickListener(null);
    }

    private void resetItemBg(ViewHolder holder) {
        holder.itemView.setBackgroundColor(Color.WHITE);
        holder.itemView.setPadding(24, 12, 24, 12);
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) holder.itemView.getLayoutParams();
        mlp.setMargins(0, 0, 0, 0);
        holder.itemView.setLayoutParams(mlp);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ========== 组标题数据 ==========

    static class GroupHeader {
        final String displayName;
        final String sourceFile;

        GroupHeader(int index, String sourceFile) {
            this.displayName = "第" + toChinese(index) + "套";
            // 取文件名作副标题
            String name = sourceFile;
            if (name != null && name.contains("/")) {
                name = name.substring(name.lastIndexOf("/") + 1);
            }
            this.sourceFile = name != null ? name : "";
        }

        private static String toChinese(int num) {
            String[] cn = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
                    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十"};
            if (num >= 0 && num < cn.length) return cn[num];
            return String.valueOf(num);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvResult;
        final TextView tvQuestion;

        ViewHolder(@NonNull View itemView, TextView tvTitle, TextView tvResult, TextView tvQuestion) {
            super(itemView);
            this.tvTitle = tvTitle;
            this.tvResult = tvResult;
            this.tvQuestion = tvQuestion;
        }
    }
}
