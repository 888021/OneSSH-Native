package com.onessh.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jcraft.jsch.ChannelSftp;
import com.onessh.app.data.Host;
import com.onessh.app.data.Store;
import com.onessh.app.ssh.HostKeyPrompt;
import com.onessh.app.ssh.SshSession;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 直接编辑远端文本文件：读出来放进编辑框，改完写回去。 */
public class FileEditActivity extends Activity {

    public static final String EXTRA_HOST_ID = "hostId";
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_NAME = "name";

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private SshSession session;
    private ChannelSftp sftp;

    private String hostId;
    private String path;
    private String name;

    private EditText editor;
    private TextView status;
    private LinearLayout overlay;
    private TextView saveAction;

    private boolean dirty;
    private boolean saving;
    private String original = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        hostId = getIntent().getStringExtra(EXTRA_HOST_ID);
        path = getIntent().getStringExtra(EXTRA_PATH);
        name = getIntent().getStringExtra(EXTRA_NAME);
        if (name == null || name.isEmpty()) {
            int i = path == null ? -1 : path.lastIndexOf('/');
            name = i >= 0 ? path.substring(i + 1) : String.valueOf(path);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout content = Widgets.darkScreen(this);

        saveAction = Widgets.textAction(this, "保存", UI.TERM_MUTED);
        saveAction.setTypeface(UI.MEDIUM);
        saveAction.setOnClickListener(v -> save());
        content.addView(Widgets.darkBar(this, name, "编辑中", this::onBack, saveAction));

        editor = new EditText(this);
        editor.setBackgroundColor(UI.TERM_BG);
        editor.setTextColor(UI.TERM_FG);
        editor.setHintTextColor(UI.TERM_MUTED);
        editor.setTextSize(12f);
        editor.setTypeface(UI.MONO);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(UI.dp(10), UI.dp(8), UI.dp(10), UI.dp(24));
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setVerticalScrollBarEnabled(true);
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                dirty = !s.toString().equals(original);
                refreshStatus();
                if (saveAction != null) {
                    saveAction.setTextColor(dirty ? UI.ACCENT : UI.TERM_MUTED);
                }
            }
        });

        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(UI.TERM_BG);
        sc.setFillViewport(true);
        sc.addView(editor, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = UI.row(this);
        foot.setBackgroundColor(UI.TERM_BAR);
        foot.setPadding(UI.dp(14), UI.dp(9), UI.dp(14), UI.dp(9));
        status = UI.text(this, "", 10.5f, UI.TERM_MUTED);
        foot.addView(status);
        UI.weight(status, 1f);
        LinearLayout wrap = UI.column(this);
        wrap.addView(UI.hline(this, UI.TERM_LINE));
        wrap.addView(foot);
        content.addView(wrap);

        overlay = UI.column(this);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(UI.TERM_BG);

        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(UI.TERM_BG);
        root.addView(content, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        load();
    }

    /* ------------------------------------------------------------- 读取 */

    private void load() {
        showOverlay("正在读取 " + name + "…");

        Host host = hostId == null ? null : Store.get(this).host(hostId);
        if (host == null) {
            showOverlay("主机不存在");
            return;
        }

        Store store = Store.get(this);
        SshSession.Config cfg = SshSession.fromHost(store, host);

        io.execute(() -> {
            SshSession s = new SshSession(cfg, new SshSession.Listener() {
                @Override
                public void onOutput(byte[] data) {
                }

                @Override
                public void onState(String state, String message) {
                }
            }, (hostPort, fp, known) -> HostKeyPrompt.ask(this, hostPort, fp, known));

            session = s;
            s.connect();
            if (!s.isConnected()) {
                showOverlay("连不上主机，检查网络和凭据");
                return;
            }

            try {
                sftp = s.openSftp();
                long size = sftp.lstat(path).getSize();
                if (size > MAX_BYTES) {
                    showOverlay("文件太大（" + FilesActivity.bytes(size) + "），这个编辑器只适合改配置文件");
                    return;
                }

                InputStream in = sftp.get(path);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                byte[] bytes = bos.toByteArray();

                if (looksBinary(bytes)) {
                    showOverlay("这看起来是二进制文件，不支持直接编辑");
                    return;
                }

                String text = new String(bytes, StandardCharsets.UTF_8);
                runOnUiThread(() -> {
                    original = text;
                    editor.setText(text);
                    editor.setSelection(0);
                    dirty = false;
                    hideOverlay();
                    refreshStatus();
                });
            } catch (Exception e) {
                showOverlay("读取失败：" + e.getMessage());
            }
        });
    }

    private static boolean looksBinary(byte[] b) {
        int limit = Math.min(b.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (b[i] == 0) return true;
        }
        return false;
    }

    /* ------------------------------------------------------------- 保存 */

    private void save() {
        if (saving) return;
        if (!dirty) {
            UI.toast(this, "没有改动");
            return;
        }
        if (sftp == null) {
            UI.toast(this, "还没连上");
            return;
        }

        saving = true;
        saveAction.setText("保存中");
        byte[] bytes = editor.getText().toString().getBytes(StandardCharsets.UTF_8);

        io.execute(() -> {
            try {
                sftp.put(new ByteArrayInputStream(bytes), path);
                runOnUiThread(() -> {
                    saving = false;
                    original = editor.getText().toString();
                    dirty = false;
                    saveAction.setText("保存");
                    saveAction.setTextColor(UI.TERM_MUTED);
                    refreshStatus();
                    UI.toast(this, "已保存 " + bytes.length + " 字节");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false;
                    saveAction.setText("保存");
                    UI.toast(this, "保存失败：" + e.getMessage());
                });
            }
        });
    }

    /* ------------------------------------------------------------- 界面 */

    private void refreshStatus() {
        if (status == null || editor == null) return;
        String text = editor.getText().toString();
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        int bytes = text.getBytes(StandardCharsets.UTF_8).length;
        status.setText(String.format(Locale.US, "%d 行 · %s%s",
                lines, FilesActivity.bytes(bytes), dirty ? " · 未保存" : ""));
    }

    private void showOverlay(String msg) {
        if (overlay == null) return;
        runOnUiThread(() -> {
            overlay.removeAllViews();
            overlay.setVisibility(View.VISIBLE);
            ProgressBar pb = new ProgressBar(this);
            pb.getIndeterminateDrawable().setTint(UI.ACCENT);
            overlay.addView(pb, new LinearLayout.LayoutParams(UI.dp(28), UI.dp(28)));
            TextView t = UI.text(this, msg, 12f, UI.TERM_MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(UI.dp(32), UI.dp(14), UI.dp(32), 0);
            overlay.addView(t);

            LinearLayout back = Widgets.link(this, "返回文件列表", null, UI.TERM_MUTED);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = UI.dp(10);
            back.setLayoutParams(lp);
            back.setOnClickListener(v -> finish());
            overlay.addView(back);
        });
    }

    private void hideOverlay() {
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void onBack() {
        if (!dirty) {
            finish();
            return;
        }
        Dialogs.confirm(this, "放弃修改？", "改动还没保存，返回就丢了。", "放弃", true, this::finish);
    }

    @Override
    public void onBackPressed() {
        onBack();
    }

    @Override
    protected void onDestroy() {
        try {
            if (sftp != null) sftp.disconnect();
        } catch (Exception ignored) {
        }
        if (session != null) session.close();
        io.shutdownNow();
        super.onDestroy();
    }
}
