package com.onessh.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.onessh.app.data.Snippet;
import com.onessh.app.data.Store;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.util.List;

/** 代码片段：把常用命令存起来，点一下就复制（终端里则是一键发送）。 */
public class SnippetsActivity extends Activity {

    private Store store;
    private LinearLayout listBox;
    private TextView countText;

    /* 编辑器状态 */
    private Snippet editing;
    private Widgets.Field nameField;
    private Widgets.Field cmdField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        showList();
    }

    /* ------------------------------------------------------------- 列表 */

    private void showList() {
        editing = null;

        LinearLayout root = Widgets.screen(this);

        TextView add = Widgets.textAction(this, "新建", UI.ACCENT);
        add.setTypeface(UI.MEDIUM);
        add.setOnClickListener(v -> showEditor(null));
        root.addView(Widgets.bar(this, "代码片段", this::finish, add));

        LinearLayout head = UI.column(this);
        head.setPadding(UI.dp(20), UI.dp(4), UI.dp(20), 0);
        head.addView(Widgets.eyebrow(this, "SNIPPETS"));
        TextView hint = Widgets.hint(this,
                "把常用命令存成片段。这里点一下是复制，在终端里点一下是直接发送。");
        hint.setLineSpacing(UI.dp(3), 1f);
        Widgets.top(hint, 6);
        head.addView(hint);
        root.addView(head);

        ScrollView sc = Widgets.scroll(this);
        listBox = UI.column(this);
        listBox.setPadding(UI.dp(20), UI.dp(16), UI.dp(20), UI.dp(16));
        sc.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = UI.row(this);
        foot.setPadding(UI.dp(20), UI.dp(10), UI.dp(20), UI.dp(12));
        countText = UI.text(this, "", 11.5f, UI.MUTED);
        foot.addView(countText);
        UI.weight(countText, 1f);
        LinearLayout wrap = UI.column(this);
        wrap.addView(UI.hline(this, UI.LINE));
        wrap.addView(foot);
        root.addView(wrap);

        setContentView(root);
        render();
    }

    private void render() {
        listBox.removeAllViews();
        List<Snippet> list = store.snippets();

        if (list.isEmpty()) {
            listBox.addView(Widgets.empty(this, "code", "还没有代码片段",
                    "把常用的命令存成片段，之后在终端里一键发送。\n例如：查看磁盘占用、重启服务。",
                    "新建片段", () -> showEditor(null)));
        } else {
            for (Snippet s : list) listBox.addView(row(s));
        }

        if (countText != null) countText.setText(list.size() + " 条片段");
    }

    private View row(Snippet s) {
        LinearLayout row = UI.row(this);
        row.setBackground(UI.ripple(UI.bg(UI.CARD, 12f, UI.LINE, 1f), 12f));
        row.setElevation(UI.dp(1f));
        UI.padding(row, 12, 12, 6, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(8);
        row.setLayoutParams(lp);

        LinearLayout tile = UI.row(this);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(UI.bg(UI.PAPER2, 10f));
        tile.addView(UI.icon(this, "code", UI.INK2, 18));
        row.addView(tile, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(36)));

        LinearLayout col = UI.column(this);
        TextView name = UI.medium(this,
                s.name == null || s.name.isEmpty() ? "未命名" : s.name, 13.5f, UI.INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(name);

        TextView cmd = UI.mono(this, s.command.replace("\n", " ⏎ "), 11f, UI.MUTED);
        cmd.setSingleLine(true);
        cmd.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Widgets.top(cmd, 3);
        col.addView(cmd);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = UI.dp(12);
        row.addView(col, cp);

        row.addView(Widgets.barIcon(this, "more", UI.MUTED, () -> Dialogs.sheet(this, s.name,
                new String[]{"复制命令", "编辑", "删除"},
                new String[]{"copy", "edit", "trash"}, 2, idx -> {
                    switch (idx) {
                        case 0:
                            copy(s.command);
                            break;
                        case 1:
                            showEditor(s);
                            break;
                        default:
                            Dialogs.confirm(this, "删除片段",
                                    "确定删除「" + s.name + "」吗？", "删除", true, () -> {
                                        store.deleteSnippet(s.id);
                                        render();
                                    });
                            break;
                    }
                })));

        row.setOnClickListener(v -> {
            copy(s.command);
            UI.toast(this, "已复制：" + s.name);
        });
        return row;
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;
        cm.setPrimaryClip(ClipData.newPlainText("snippet", text));
    }

    /* ------------------------------------------------------------- 编辑器 */

    private void showEditor(Snippet snippet) {
        editing = snippet != null ? snippet : new Snippet();

        LinearLayout root = Widgets.screen(this);

        TextView save = Widgets.textAction(this, "保存", UI.ACCENT);
        save.setTypeface(UI.MEDIUM);
        save.setOnClickListener(v -> save());
        root.addView(Widgets.bar(this,
                snippet == null ? "新建片段" : "编辑片段", this::showList, save));

        ScrollView sc = Widgets.scroll(this);
        LinearLayout body = UI.column(this);
        body.setPadding(UI.dp(20), UI.dp(6), UI.dp(20), UI.dp(24));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout card = Widgets.cardBox(this);
        card.addView(Widgets.cardTitle(this, "片段"));

        nameField = Widgets.field(this, "名称", "例如：查看磁盘占用", InputType.TYPE_CLASS_TEXT);
        nameField.setText(editing.name);
        card.addView(nameField.root);

        cmdField = Widgets.field(this, "命令",
                "例如：df -h\ntop -b -n1 | head -20",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        cmdField.input.setTypeface(UI.MONO);
        cmdField.input.setTextSize(12f);
        cmdField.input.setMinLines(4);
        cmdField.input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        cmdField.setText(editing.command);
        Widgets.top(cmdField.root, 12);
        card.addView(cmdField.root);

        body.addView(card);

        TextView note = UI.text(this,
                "命令按原样发送（末尾会自动补一个回车）。多行命令会逐行发送。", 11.5f, UI.MUTED);
        note.setLineSpacing(UI.dp(3), 1f);
        Widgets.top(note, 14);
        body.addView(note);

        setContentView(root);
        if (snippet == null) nameField.input.requestFocus();
    }

    private void save() {
        String name = nameField.text().trim();
        String command = cmdField.text().trim();

        if (command.isEmpty()) {
            UI.toast(this, "命令不能为空");
            return;
        }
        if (name.isEmpty()) {
            // 用命令的第一行兜底当名字
            String first = command.split("\n")[0].trim();
            name = first.length() > 24 ? first.substring(0, 24) + "…" : first;
        }

        editing.name = name;
        editing.command = command;
        store.saveSnippet(editing);
        UI.toast(this, "已保存");
        showList();
    }

    @Override
    public void onBackPressed() {
        if (editing != null) showList();
        else super.onBackPressed();
    }
}
