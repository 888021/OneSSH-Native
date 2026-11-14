package com.onessh.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.onessh.app.data.Host;
import com.onessh.app.data.Store;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.util.List;

/** 分组管理：新建、重命名（同步更新组内主机）、删除（主机回到未分组）。 */
public class GroupsActivity extends Activity {

    private Store store;
    private LinearLayout listBox;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        build();
    }

    private void build() {
        LinearLayout root = Widgets.screen(this);

        TextView add = Widgets.textAction(this, "新建", UI.ACCENT);
        add.setTypeface(UI.MEDIUM);
        add.setOnClickListener(v -> newGroup());
        root.addView(Widgets.bar(this, "分组", this::finish, add));

        LinearLayout head = UI.column(this);
        head.setPadding(UI.dp(20), UI.dp(4), UI.dp(20), 0);
        head.addView(Widgets.eyebrow(this, "GROUPS"));
        TextView hint = Widgets.hint(this,
                "把主机按环境或项目归类。重命名会同步更新组内主机，删除后主机会回到「未分组」。");
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
        List<String> groups = store.groups();
        List<Host> hosts = store.hosts();

        if (groups.isEmpty()) {
            listBox.addView(Widgets.empty(this, "folder", "还没有分组",
                    "把主机按环境或项目归类，列表会更清爽。\n比如「生产」「测试」「客户 A」。",
                    "新建分组", this::newGroup));
        } else {
            for (String g : groups) {
                int n = 0;
                for (Host h : hosts) if (g.equals(h.group)) n++;
                listBox.addView(row(g, n));
            }
        }

        if (countText != null) {
            countText.setText(groups.size() + " 个分组 · " + hosts.size() + " 台主机");
        }
    }

    private View row(String name, int count) {
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
        tile.setBackground(UI.bg(UI.ACCENT_SOFT, 10f));
        tile.addView(UI.icon(this, "folder", UI.ACCENT, 18));
        row.addView(tile, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(36)));

        LinearLayout col = UI.column(this);
        TextView t = UI.medium(this, name, 13.5f, UI.INK);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(t);
        TextView s = UI.text(this, count + " 台主机", 11f, UI.MUTED);
        Widgets.top(s, 3);
        col.addView(s);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = UI.dp(12);
        row.addView(col, cp);

        row.addView(Widgets.barIcon(this, "more", UI.MUTED, () -> Dialogs.sheet(this, name,
                new String[]{"重命名", "删除"}, new String[]{"edit", "trash"}, 1, idx -> {
                    if (idx == 0) rename(name);
                    else remove(name, count);
                })));

        row.setOnClickListener(v -> rename(name));
        return row;
    }

    private void newGroup() {
        Dialogs.prompt(this, "新建分组", "分组名称", "例如：生产环境", "", false, name -> {
            String g = name.trim();
            if (g.isEmpty()) return;
            if (store.groups().contains(g)) {
                UI.toast(this, "这个分组已经存在");
                return;
            }
            store.addGroup(g);
            render();
            UI.toast(this, "已创建「" + g + "」");
        });
    }

    private void rename(String from) {
        Dialogs.prompt(this, "重命名分组", "新的名称", null, from, false, name -> {
            String to = name.trim();
            if (to.isEmpty() || to.equals(from)) return;
            if (store.groups().contains(to)) {
                UI.toast(this, "已经有同名分组了");
                return;
            }
            int moved = store.renameGroup(from, to);
            render();
            UI.toast(this, moved > 0
                    ? "已重命名，" + moved + " 台主机同步更新"
                    : "已重命名");
        });
    }

    private void remove(String name, int count) {
        Dialogs.confirm(this, "删除分组",
                count > 0
                        ? "确定删除「" + name + "」吗？组内 " + count + " 台主机会移到「未分组」，主机本身不会被删除。"
                        : "确定删除「" + name + "」吗？",
                "删除", true, () -> {
                    int moved = store.removeGroup(name);
                    render();
                    UI.toast(this, moved > 0 ? "已删除，" + moved + " 台主机移到未分组" : "已删除");
                });
    }
}
