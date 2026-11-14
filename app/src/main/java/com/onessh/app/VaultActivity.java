package com.onessh.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.onessh.app.data.Credential;
import com.onessh.app.data.Crypto;
import com.onessh.app.data.KeyItem;
import com.onessh.app.data.Store;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.util.ArrayList;
import java.util.List;

/** 凭据库：常用账号 + 私钥。列表和编辑器在同一个 Activity 里切换。 */
public class VaultActivity extends Activity {

    private Store store;

    private int tab = 0; // 0 账号，1 私钥
    private LinearLayout listRoot;
    private LinearLayout listBox;
    private TextView addAction;

    /* 编辑器状态 */
    private Credential editingCred;
    private KeyItem editingKey;
    private Widgets.Field nameField;
    private Widgets.Field userField;
    private Widgets.Field passField;
    private Widgets.Field keyBodyField;
    private Widgets.Field keyPassField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);
        showList();
    }

    /* ------------------------------------------------------------- 列表 */

    private void showList() {
        editingCred = null;
        editingKey = null;

        LinearLayout root = Widgets.screen(this);

        addAction = Widgets.textAction(this,
                tab == 0 ? getString(R.string.add_account) : getString(R.string.add_key), UI.ACCENT);
        addAction.setTypeface(UI.MEDIUM);
        addAction.setOnClickListener(v -> {
            if (tab == 0) showCredEditor(null);
            else showKeyEditor(null);
        });
        root.addView(Widgets.bar(this, getString(R.string.vault_title), this::finish, addAction));

        LinearLayout head = UI.column(this);
        head.setPadding(UI.dp(20), UI.dp(4), UI.dp(20), 0);
        head.addView(Widgets.eyebrow(this, "VAULT"));
        TextView sub = Widgets.hint(this, getString(R.string.vault_sub));
        Widgets.top(sub, 6);
        head.addView(sub);
        root.addView(head);

        LinearLayout tabsHost = UI.column(this);
        tabsHost.setPadding(UI.dp(20), UI.dp(16), UI.dp(20), 0);
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.tab_accounts));
        labels.add(getString(R.string.tab_keys));
        List<String> counts = new ArrayList<>();
        counts.add(String.valueOf(store.creds().size()));
        counts.add(String.valueOf(store.keys().size()));
        tabsHost.addView(Widgets.tabs(this, labels, counts, tab, idx -> {
            tab = idx;
            showList();
        }));
        root.addView(tabsHost);

        ScrollView sc = Widgets.scroll(this);
        listBox = UI.column(this);
        listBox.setPadding(UI.dp(20), UI.dp(16), UI.dp(20), UI.dp(20));
        sc.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        renderList();

        listRoot = root;
        setContentView(root);
    }

    private void renderList() {
        listBox.removeAllViews();

        if (tab == 0) {
            List<Credential> creds = store.creds();
            if (creds.isEmpty()) {
                listBox.addView(Widgets.empty(this, "lock", getString(R.string.vault_empty_accounts),
                        "把常用的「用户名 + 密码」存一次，多台主机都能引用。",
                        getString(R.string.add_account), () -> showCredEditor(null)));
                return;
            }
            for (Credential c : creds) {
                listBox.addView(row("lock", c.name, c.username,
                        () -> showCredEditor(c), () -> deleteCred(c)));
            }
        } else {
            List<KeyItem> keys = store.keys();
            if (keys.isEmpty()) {
                listBox.addView(Widgets.empty(this, "key", getString(R.string.vault_empty_keys),
                        "导入一次私钥，之后所有主机都能选它登录。",
                        getString(R.string.add_key), () -> showKeyEditor(null)));
                return;
            }
            for (KeyItem k : keys) {
                String sub = k.algo == null || k.algo.isEmpty() ? "私钥" : k.algo;
                if (k.fingerprint != null && !k.fingerprint.isEmpty()) sub += " · " + k.fingerprint;
                listBox.addView(row("key", k.name, sub,
                        () -> showKeyEditor(k), () -> deleteKey(k)));
            }
        }
    }

    private View row(String iconName, String title, String sub, Runnable onEdit, Runnable onDelete) {
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
        tile.addView(UI.icon(this, iconName, UI.ACCENT, 18));
        row.addView(tile, new LinearLayout.LayoutParams(UI.dp(36), UI.dp(36)));

        LinearLayout col = UI.column(this);
        TextView t = UI.medium(this, title == null || title.isEmpty() ? "未命名" : title, 13.5f, UI.INK);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = UI.mono(this, sub, 11f, UI.MUTED);
            s.setSingleLine(true);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Widgets.top(s, 3);
            col.addView(s);
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = UI.dp(12);
        row.addView(col, cp);

        row.addView(Widgets.barIcon(this, "more", UI.MUTED, () -> Dialogs.sheet(this,
                title == null || title.isEmpty() ? "未命名" : title,
                new String[]{"编辑", "删除"}, new String[]{"edit", "trash"}, 1, idx -> {
                    if (idx == 0) onEdit.run();
                    else onDelete.run();
                })));

        row.setOnClickListener(v -> onEdit.run());
        return row;
    }

    private void deleteCred(Credential c) {
        int used = store.hostCountUsingCred(c.id);
        if (used > 0) {
            UI.toast(this, getString(R.string.in_use, used));
            return;
        }
        Dialogs.confirm(this, "删除账号", "确定删除「" + c.name + "」吗？", "删除", true, () -> {
            store.deleteCred(c.id);
            showList();
        });
    }

    private void deleteKey(KeyItem k) {
        int used = store.hostCountUsingKey(k.id);
        if (used > 0) {
            UI.toast(this, getString(R.string.in_use, used));
            return;
        }
        Dialogs.confirm(this, "删除私钥", "确定删除「" + k.name + "」吗？", "删除", true, () -> {
            store.deleteKey(k.id);
            showList();
        });
    }

    /* ------------------------------------------------------------- 编辑器 */

    private void showCredEditor(Credential cred) {
        editingCred = cred != null ? cred : new Credential();
        editingKey = null;

        LinearLayout root = Widgets.screen(this);
        TextView save = Widgets.textAction(this, getString(R.string.save), UI.ACCENT);
        save.setTypeface(UI.MEDIUM);
        save.setOnClickListener(v -> saveCred());
        root.addView(Widgets.bar(this,
                cred == null ? getString(R.string.add_account) : getString(R.string.tab_accounts),
                this::showList, save));

        ScrollView sc = Widgets.scroll(this);
        LinearLayout body = UI.column(this);
        body.setPadding(UI.dp(20), UI.dp(6), UI.dp(20), UI.dp(24));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout card = Widgets.cardBox(this);
        card.addView(Widgets.cardTitle(this, "账号"));

        nameField = Widgets.field(this, getString(R.string.cred_name), "例如：生产环境 root",
                InputType.TYPE_CLASS_TEXT);
        nameField.setText(editingCred.name);
        card.addView(nameField.root);

        userField = Widgets.field(this, getString(R.string.cred_user), "root",
                InputType.TYPE_CLASS_TEXT);
        userField.input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        userField.setText(editingCred.username);
        Widgets.top(userField.root, 12);
        card.addView(userField.root);

        passField = Widgets.field(this, getString(R.string.cred_pass),
                editingCred.passwordEnc.isEmpty() ? "登录密码" : getString(R.string.field_password_keep),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Widgets.top(passField.root, 12);
        card.addView(passField.root);

        body.addView(card);

        TextView note = UI.text(this, "密码用 Android Keystore 加密后保存在本机，不会上传到任何服务器。",
                11.5f, UI.MUTED);
        note.setLineSpacing(UI.dp(3), 1f);
        Widgets.top(note, 14);
        body.addView(note);

        setContentView(root);
        nameField.input.requestFocus();
    }

    private void saveCred() {
        String name = nameField.text().trim();
        String user = userField.text().trim();
        String pass = passField.text();

        if (user.isEmpty()) {
            UI.toast(this, "用户名不能为空");
            return;
        }
        if (pass.isEmpty() && editingCred.passwordEnc.isEmpty()) {
            UI.toast(this, "密码不能为空");
            return;
        }
        if (name.isEmpty()) name = user;

        editingCred.name = name;
        editingCred.username = user;
        if (!pass.isEmpty()) {
            String enc = Crypto.encrypt(pass);
            if (enc == null) {
                UI.toast(this, "加密失败，未保存");
                return;
            }
            editingCred.passwordEnc = enc;
        }

        store.saveCred(editingCred);
        UI.toast(this, "已保存");
        showList();
    }

    private void showKeyEditor(KeyItem key) {
        editingKey = key != null ? key : new KeyItem();
        editingCred = null;

        LinearLayout root = Widgets.screen(this);
        TextView save = Widgets.textAction(this, getString(R.string.save), UI.ACCENT);
        save.setTypeface(UI.MEDIUM);
        save.setOnClickListener(v -> saveKey());
        root.addView(Widgets.bar(this,
                key == null ? getString(R.string.add_key) : getString(R.string.tab_keys),
                this::showList, save));

        ScrollView sc = Widgets.scroll(this);
        LinearLayout body = UI.column(this);
        body.setPadding(UI.dp(20), UI.dp(6), UI.dp(20), UI.dp(24));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout card = Widgets.cardBox(this);
        card.addView(Widgets.cardTitle(this, "私钥"));

        nameField = Widgets.field(this, "名称", "例如：工作机 ed25519", InputType.TYPE_CLASS_TEXT);
        nameField.setText(editingKey.name);
        card.addView(nameField.root);

        keyBodyField = Widgets.field(this, getString(R.string.key_body),
                editingKey.keyEnc.isEmpty() ? getString(R.string.key_body_hint)
                        : "已保存私钥，留空则不修改",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        keyBodyField.input.setTypeface(UI.MONO);
        keyBodyField.input.setTextSize(11f);
        keyBodyField.input.setMinLines(6);
        Widgets.top(keyBodyField.root, 12);
        card.addView(keyBodyField.root);

        keyPassField = Widgets.field(this, getString(R.string.key_passphrase),
                getString(R.string.key_passphrase_hint),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Widgets.top(keyPassField.root, 12);
        card.addView(keyPassField.root);

        body.addView(card);

        TextView note = UI.text(this,
                "支持 OpenSSH（-----BEGIN OPENSSH PRIVATE KEY-----）、RSA/ECDSA PEM 格式。私钥同样用系统密钥库加密。",
                11.5f, UI.MUTED);
        note.setLineSpacing(UI.dp(3), 1f);
        Widgets.top(note, 14);
        body.addView(note);

        setContentView(root);
    }

    private void saveKey() {
        String name = nameField.text().trim();
        String body = keyBodyField.text().trim();
        String pass = keyPassField.text();

        if (body.isEmpty() && editingKey.keyEnc.isEmpty()) {
            UI.toast(this, "请粘贴私钥内容");
            return;
        }
        if (!body.isEmpty() && !body.contains("PRIVATE KEY")) {
            UI.toast(this, "看起来不是私钥文件");
            return;
        }
        if (name.isEmpty()) name = "私钥 " + (store.keys().size() + 1);

        editingKey.name = name;
        if (!body.isEmpty()) {
            String enc = Crypto.encrypt(body);
            if (enc == null) {
                UI.toast(this, "加密失败，未保存");
                return;
            }
            editingKey.keyEnc = enc;
            editingKey.algo = guessAlgo(body);
            editingKey.fingerprint = "";
        }
        if (!pass.isEmpty()) {
            String enc = Crypto.encrypt(pass);
            if (enc == null) {
                UI.toast(this, "加密失败，未保存");
                return;
            }
            editingKey.passphraseEnc = enc;
        }

        store.saveKey(editingKey);
        UI.toast(this, "已保存");
        showList();
    }

    private static String guessAlgo(String body) {
        if (body.contains("OPENSSH PRIVATE KEY")) return "OpenSSH";
        if (body.contains("RSA PRIVATE KEY")) return "RSA";
        if (body.contains("EC PRIVATE KEY")) return "ECDSA";
        if (body.contains("DSA PRIVATE KEY")) return "DSA";
        return "私钥";
    }

    @Override
    public void onBackPressed() {
        // 编辑器里按返回等于取消，回到列表
        if (editingCred != null || editingKey != null) {
            showList();
        } else {
            super.onBackPressed();
        }
    }
}
