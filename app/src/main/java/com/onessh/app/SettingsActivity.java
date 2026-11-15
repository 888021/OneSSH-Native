package com.onessh.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.onessh.app.data.Store;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 设置：字号、常亮、应用锁、已信任的主机密钥、备份 / 导入、清空数据、关于。 */
public class SettingsActivity extends Activity {

    private static final int REQ_EXPORT = 1001;
    private static final int REQ_IMPORT = 1002;

    private Store store;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        build();
    }

    private void build() {
        root = Widgets.screen(this);
        root.addView(Widgets.bar(this, getString(R.string.settings_title), this::finish, null));

        ScrollView sc = Widgets.scroll(this);
        LinearLayout body = UI.column(this);
        body.setPadding(UI.dp(20), UI.dp(6), UI.dp(20), UI.dp(28));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        /* 终端 */
        LinearLayout term = Widgets.cardBox(this);
        term.addView(Widgets.cardTitle(this, getString(R.string.settings_terminal)));
        term.addView(fontRow());
        term.addView(UI.hline(this, UI.LINE2));
        term.addView(keepAwakeRow());
        body.addView(term);
        Widgets.bottom(term, 14);

        /* 安全 */
        LinearLayout sec = Widgets.cardBox(this);
        sec.addView(Widgets.cardTitle(this, getString(R.string.settings_security)));
        sec.addView(appLockRow());
        sec.addView(UI.hline(this, UI.LINE2));

        Map<String, String> known = store.knownHosts();
        if (known.isEmpty()) {
            sec.addView(UI.text(this, getString(R.string.settings_known_empty), 12f, UI.MUTED2));
        } else {
            for (Map.Entry<String, String> e : known.entrySet()) {
                sec.addView(knownRow(e.getKey(), e.getValue()));
            }
        }
        body.addView(sec);
        Widgets.bottom(sec, 14);

        /* 数据 */
        LinearLayout data = Widgets.cardBox(this);
        data.addView(Widgets.cardTitle(this, "数据"));
        TextView backupHint = UI.text(this,
                "备份包含主机、凭据和片段；密码 / 私钥仍是本机 Keystore 密文，换设备后要重填，不要发给别人。",
                11f, UI.MUTED2);
        backupHint.setLineSpacing(UI.dp(3), 1f);
        data.addView(backupHint);
        LinearLayout exportBtn = Widgets.button(this, "导出备份", "download", false);
        Widgets.top(exportBtn, 10);
        exportBtn.setOnClickListener(v -> exportBackup());
        data.addView(exportBtn);
        LinearLayout importBtn = Widgets.button(this, "导入备份", "upload", false);
        Widgets.top(importBtn, 8);
        importBtn.setOnClickListener(v -> importBackup());
        data.addView(importBtn);
        data.addView(UI.hline(this, UI.LINE2));
        LinearLayout clear = Widgets.button(this, getString(R.string.settings_clear), "trash", false);
        ((TextView) clear.getChildAt(1)).setTextColor(UI.RED);
        clear.setBackground(UI.ripple(UI.bg(0xFFFFF7F6, 10f, 0xFFF0D5D1, 1f), 10f));
        clear.setOnClickListener(v -> Dialogs.confirm(this, getString(R.string.settings_clear),
                getString(R.string.settings_clear_msg), "全部删除", true, () -> {
                    store.clearAll();
                    UI.toast(this, "已清空");
                    build();
                }));
        data.addView(clear);
        body.addView(data);
        Widgets.bottom(data, 14);

        /* 关于 */
        LinearLayout about = Widgets.cardBox(this);
        about.addView(Widgets.cardTitle(this, getString(R.string.settings_about)));
        TextView ver = UI.medium(this, "OneSSH " + BuildConfig.VERSION_NAME, 13f, UI.INK);
        about.addView(ver);
        TextView txt = UI.text(this, getString(R.string.settings_about_body, BuildConfig.VERSION_NAME),
                11.5f, UI.MUTED);
        txt.setLineSpacing(UI.dp(3), 1f);
        Widgets.top(txt, 8);
        about.addView(txt);
        body.addView(about);

        setContentView(root);
    }

    private View fontRow() {
        LinearLayout row = UI.row(this);
        UI.padding(row, 0, 10, 0, 10);

        LinearLayout col = UI.column(this);
        col.addView(UI.text(this, getString(R.string.settings_font), 13.5f, UI.INK));
        col.addView(UI.text(this, "终端里等宽字体的字号", 11f, UI.MUTED2));
        row.addView(col);
        UI.weight(col, 1f);

        row.addView(stepButton("-", () -> {
            store.setFontSize(store.fontSize() - 1);
            build();
        }));

        TextView value = UI.mono(this, store.fontSize() + " sp", 13f, UI.INK);
        value.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(UI.dp(52),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        value.setLayoutParams(vp);
        row.addView(value);

        row.addView(stepButton("+", () -> {
            store.setFontSize(store.fontSize() + 1);
            build();
        }));
        return row;
    }

    private View stepButton(String label, Runnable action) {
        TextView t = UI.medium(this, label, 16f, UI.INK);
        t.setGravity(Gravity.CENTER);
        t.setBackground(UI.ripple(UI.bg(UI.PAPER2, 8f, UI.LINE, 1f), 8f));
        t.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(34), UI.dp(30)));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private View keepAwakeRow() {
        LinearLayout row = UI.row(this);
        UI.padding(row, 0, 12, 0, 4);

        LinearLayout col = UI.column(this);
        col.addView(UI.text(this, getString(R.string.settings_keepawake), 13.5f, UI.INK));
        col.addView(UI.text(this, "连着的终端页不会自动息屏", 11f, UI.MUTED2));
        row.addView(col);
        UI.weight(col, 1f);

        Widgets.Toggle sw = new Widgets.Toggle(this, store.keepAwake());
        sw.setOnChange(store::setKeepAwake);
        row.addView(sw);
        return row;
    }

    /** 2.2.0 应用锁：打开 App 时用锁屏密码 / 指纹验证一次。 */
    private View appLockRow() {
        LinearLayout row = UI.row(this);
        UI.padding(row, 0, 12, 0, 12);

        LinearLayout col = UI.column(this);
        col.addView(UI.text(this, "应用锁", 13.5f, UI.INK));
        col.addView(UI.text(this, "打开 App 时用锁屏密码 / 指纹验证，没设锁屏就直接进", 11f, UI.MUTED2));
        row.addView(col);
        UI.weight(col, 1f);

        Widgets.Toggle sw = new Widgets.Toggle(this, store.appLock());
        sw.setOnChange(on -> {
            store.setAppLock(on);
            UI.toast(this, on ? "已开启应用锁，下次打开要验证" : "已关闭应用锁");
        });
        row.addView(sw);
        return row;
    }

    /* --------------------------------------------------------- 备份 / 导入 */

    private void exportBackup() {
        String text = store.exportBackup();
        if (text == null || text.isEmpty()) {
            UI.toast(this, "没什么可备份的");
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "onessh-backup.json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            UI.toast(this, "没有可用的文件选择器");
        }
    }

    private void importBackup() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            UI.toast(this, "没有可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            String text = store.exportBackup();
            if (text == null) return;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("打不开目标文件");
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
                UI.toast(this, "备份已导出");
            } catch (Exception e) {
                UI.toast(this, "导出失败：" + e.getMessage());
            }
        } else if (requestCode == REQ_IMPORT) {
            String text;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("读不到文件");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                text = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                UI.toast(this, "读取失败：" + e.getMessage());
                return;
            }
            final String backupText = text;
            Dialogs.confirm(this, "导入备份",
                    "会用备份里的内容整体替换现在的主机、凭据、片段和设置，当前数据会被覆盖。建议先导出一份备份再导入。",
                    "导入并覆盖", true, () -> {
                        if (store.importBackup(backupText)) {
                            UI.toast(this, "已导入备份");
                            build();
                        } else {
                            UI.toast(this, "导入失败：不是 OneSSH 的备份文件");
                        }
                    });
        }
    }

    private View knownRow(String hostPort, String fingerprint) {
        LinearLayout row = UI.row(this);
        row.setBackground(UI.ripple(0x00000000, 8f));
        UI.padding(row, 0, 9, 0, 9);

        LinearLayout col = UI.column(this);
        col.addView(UI.medium(this, hostPort, 12.5f, UI.INK2));
        TextView fp = UI.mono(this, fingerprint, 10f, UI.MUTED);
        fp.setSingleLine(true);
        fp.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        Widgets.top(fp, 3);
        col.addView(fp);
        row.addView(col);
        UI.weight(col, 1f);

        row.addView(Widgets.barIcon(this, "trash", UI.MUTED2, () ->
                Dialogs.confirm(this, "忘记这台主机",
                        "删除后下次连接 " + hostPort + " 会重新询问指纹。",
                        "删除", true, () -> {
                            store.forgetHost(hostPort);
                            build();
                        })));

        row.setOnClickListener(v -> Dialogs.alert(this, hostPort, fingerprint));
        return row;
    }
}
