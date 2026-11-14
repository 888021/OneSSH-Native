package com.onessh.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.onessh.app.data.Host;
import com.onessh.app.data.Store;
import com.onessh.app.ssh.HostKeyPrompt;
import com.onessh.app.ssh.SshSession;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SFTP 文件浏览：列目录、进目录、上传、下载、改名、删除。 */
public class FilesActivity extends Activity {

    public static final String EXTRA_HOST_ID = "hostId";
    /** 调试用：不连服务器，直接渲染一份示例目录 */
    public static final String EXTRA_DEMO = "demo";

    private static final int REQ_UPLOAD = 1001;

    private Store store;
    private Host host;
    private String hostId;
    private SshSession session;
    private ChannelSftp sftp;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private LinearLayout listBox;
    private TextView pathText;
    private TextView statusText;
    private LinearLayout overlay;

    private String cwd = "/";
    private boolean connected;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = Store.get(this);

        boolean demo = getIntent().getBooleanExtra(EXTRA_DEMO, false);
        String id = getIntent().getStringExtra(EXTRA_HOST_ID);
        hostId = id;
        host = id == null ? null : store.host(id);
        if (host == null && !demo) {
            UI.toast(this, "主机不存在");
            finish();
            return;
        }

        LinearLayout content = Widgets.darkScreen(this);
        content.addView(buildBar());
        content.addView(buildPathBar());

        ScrollView sc = Widgets.scroll(this);
        listBox = UI.column(this);
        listBox.setPadding(UI.dp(10), UI.dp(6), UI.dp(10), UI.dp(16));
        sc.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildFooter());

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

        if (demo) {
            listBox.post(this::runDemo);
            return;
        }
        connect();
    }

    /* ------------------------------------------------------------- 顶栏 */

    private View buildBar() {
        LinearLayout bar = UI.row(this);
        bar.setBackgroundColor(UI.TERM_BAR);
        bar.setPadding(UI.dp(4), UI.dp(6), UI.dp(4), UI.dp(6));

        bar.addView(Widgets.barIcon(this, "back", UI.TERM_FG, this::finish));

        LinearLayout titles = UI.column(this);
        titles.addView(UI.medium(this, "文件", 13.5f, UI.TERM_FG));
        TextView sub = UI.text(this, host == null ? "示例主机" : host.label(), 10f, UI.TERM_MUTED);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Widgets.top(sub, 2);
        titles.addView(sub);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = UI.dp(4);
        bar.addView(titles, tp);

        bar.addView(Widgets.barIcon(this, "refresh", UI.TERM_MUTED, () -> {
            if (connected) list(cwd);
        }));
        bar.addView(Widgets.barIcon(this, "upload", UI.TERM_MUTED, this::pickUpload));
        bar.addView(Widgets.barIcon(this, "more", UI.TERM_MUTED, () -> Dialogs.sheet(this, null,
                new String[]{"新建文件夹", "回到主目录", "刷新", "断开"},
                new String[]{"plus", "home", "refresh", "power"}, 3, idx -> {
                    switch (idx) {
                        case 0:
                            newFolder();
                            break;
                        case 1:
                            io.execute(() -> {
                                try {
                                    String home = sftp.getHome();
                                    runOnUiThread(() -> list(home));
                                } catch (Exception e) {
                                    toastOnUi("读取主目录失败");
                                }
                            });
                            break;
                        case 2:
                            list(cwd);
                            break;
                        default:
                            finish();
                            break;
                    }
                })));
        return bar;
    }

    private View buildPathBar() {
        LinearLayout wrap = UI.row(this);
        wrap.setBackgroundColor(UI.TERM_BG);
        wrap.setPadding(UI.dp(6), UI.dp(6), UI.dp(8), UI.dp(6));

        View up = Widgets.barIcon(this, "up", UI.TERM_FG, () -> {
            if (!connected || "/".equals(cwd)) return;
            int i = cwd.lastIndexOf('/');
            String parent = i <= 0 ? "/" : cwd.substring(0, i);
            list(parent);
        });
        wrap.addView(up);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        pathText = UI.mono(this, "/", 11.5f, UI.TERM_FG);
        pathText.setSingleLine(true);
        pathText.setPadding(UI.dp(6), UI.dp(6), UI.dp(6), UI.dp(6));
        // 必须显式给 WRAP_CONTENT：默认的 MATCH_PARENT 会让它按第一次的宽度定死
        hs.addView(pathText, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(hs, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout col = UI.column(this);
        col.addView(wrap);
        col.addView(UI.hline(this, UI.TERM_LINE));
        return col;
    }

    private View buildFooter() {
        LinearLayout foot = UI.row(this);
        foot.setBackgroundColor(UI.TERM_BAR);
        foot.setPadding(UI.dp(14), UI.dp(9), UI.dp(14), UI.dp(9));

        statusText = UI.text(this, "", 10.5f, UI.TERM_MUTED);
        foot.addView(statusText);
        UI.weight(statusText, 1f);

        LinearLayout mk = Widgets.link(this, "新建文件夹", "plus", UI.TERM_MUTED);
        mk.setOnClickListener(v -> newFolder());
        foot.addView(mk);

        LinearLayout col = UI.column(this);
        col.addView(UI.hline(this, UI.TERM_LINE));
        col.addView(foot);
        return col;
    }

    /* ------------------------------------------------------------- 连接 */

    private void connect() {
        showOverlay("正在连接 " + host.subtitle() + "…");

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
                showOverlay("连接失败。检查地址、端口和凭据。");
                return;
            }
            try {
                sftp = s.openSftp();
                connected = true;
                String home = sftp.getHome();
                runOnUiThread(() -> list(home));
            } catch (Exception e) {
                showOverlay("SFTP 通道打不开：" + e.getMessage());
            }
        });
    }

    /* ------------------------------------------------------------- 列表 */

    /** 一行目录项，跟 SFTP 解耦，方便调试时造假数据 */
    static final class Item {
        String name;
        boolean dir;
        long size;
        long mtime;
    }

    private void list(String path) {
        if (!connected || loading) return;
        loading = true;
        String target = path == null || path.isEmpty() ? cwd : path;

        io.execute(() -> {
            try {
                Vector<ChannelSftp.LsEntry> entries = sftp.ls(target);
                List<Item> items = new ArrayList<>();
                for (ChannelSftp.LsEntry e : entries) {
                    String n = e.getFilename();
                    if (".".equals(n) || "..".equals(n)) continue;
                    SftpATTRS a = e.getAttrs();
                    Item it = new Item();
                    it.name = n;
                    it.dir = a.isDir();
                    it.size = a.getSize();
                    it.mtime = a.getMTime() * 1000L;
                    items.add(it);
                }
                sort(items);

                cwd = target;
                runOnUiThread(() -> {
                    loading = false;
                    render(items);
                });
            } catch (SftpException e) {
                loading = false;
                toastOnUi("打开失败：" + e.getMessage());
            }
        });
    }

    private static void sort(List<Item> items) {
        Collections.sort(items, (a, b) -> {
            if (a.dir != b.dir) return a.dir ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    private void render(List<Item> items) {
        hideOverlay();
        pathText.setText(cwd);
        pathText.requestLayout();
        listBox.removeAllViews();

        if (items.isEmpty()) {
            TextView t = UI.text(this, getString(R.string.files_empty), 12f, UI.TERM_MUTED);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UI.dp(40), 0, 0);
            listBox.addView(t);
        }

        for (Item it : items) listBox.addView(buildRow(it));

        if (statusText != null) statusText.setText(items.size() + " 项 · " + cwd);
    }

    private View buildRow(Item it) {
        LinearLayout row = UI.row(this);
        row.setBackground(UI.ripple(UI.bg(UI.TERM_LINE, 10f), 10f));
        UI.padding(row, 12, 11, 12, 11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(4);
        row.setLayoutParams(lp);

        ImageView icon = UI.icon(this, it.dir ? "folder" : "file",
                it.dir ? UI.ACCENT : UI.TERM_MUTED, 18);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(UI.dp(18), UI.dp(18));
        ip.rightMargin = UI.dp(11);
        icon.setLayoutParams(ip);
        row.addView(icon);

        LinearLayout col = UI.column(this);
        TextView t = UI.medium(this, it.name, 13f, UI.TERM_FG);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        col.addView(t);

        String meta = it.dir ? "目录" : bytes(it.size);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(it.mtime));
        TextView m = UI.mono(this, meta + " · " + time, 10f, UI.TERM_MUTED);
        m.setSingleLine(true);
        Widgets.top(m, 3);
        col.addView(m);

        row.addView(col);
        UI.weight(col, 1f);

        row.setOnClickListener(v -> {
            if (it.dir) list(join(cwd, it.name));
            else if (it.size <= 256 * 1024) openEditor(it);
            else fileMenu(it);
        });
        row.setOnLongClickListener(v -> {
            fileMenu(it);
            return true;
        });
        return row;
    }

    private void fileMenu(Item it) {
        if (it.dir) {
            Dialogs.sheet(this, it.name,
                    new String[]{"打开", "重命名", "删除"},
                    new String[]{"folder", "edit", "trash"}, 2, idx -> {
                        switch (idx) {
                            case 0:
                                list(join(cwd, it.name));
                                break;
                            case 1:
                                rename(it.name);
                                break;
                            default:
                                remove(it.name, true);
                                break;
                        }
                    });
        } else {
            Dialogs.sheet(this, it.name + " · " + bytes(it.size),
                    new String[]{"编辑文本", "下载到本机", "重命名", "删除"},
                    new String[]{"code", "download", "edit", "trash"}, 3, idx -> {
                        switch (idx) {
                            case 0:
                                openEditor(it);
                                break;
                            case 1:
                                download(it.name, it.size);
                                break;
                            case 2:
                                rename(it.name);
                                break;
                            default:
                                remove(it.name, false);
                                break;
                        }
                    });
        }
    }

    /** 打开内置编辑器改远端文件 */
    private void openEditor(Item it) {
        if (hostId == null) {
            UI.toast(this, "只有保存过的主机才能编辑文件");
            return;
        }
        Intent i = new Intent(this, FileEditActivity.class);
        i.putExtra(FileEditActivity.EXTRA_HOST_ID, hostId);
        i.putExtra(FileEditActivity.EXTRA_PATH, join(cwd, it.name));
        i.putExtra(FileEditActivity.EXTRA_NAME, it.name);
        startActivity(i);
    }

    /* ------------------------------------------------------------- 演示 */

    private void runDemo() {
        cwd = "/srv/app";
        List<Item> items = new ArrayList<>();
        items.add(demo("bin", true, 0, "2026-11-10 09:12"));
        items.add(demo("config", true, 0, "2026-11-13 21:40"));
        items.add(demo("logs", true, 0, "2026-11-14 11:01"));
        items.add(demo("README.md", false, 4382, "2026-11-08 16:22"));
        items.add(demo("app.log", false, 812, "2026-11-14 09:58"));
        items.add(demo("deploy.sh", false, 2140, "2026-11-12 08:03"));
        items.add(demo("server", false, 1258291, "2026-11-13 22:11"));
        render(items);
    }

    private static Item demo(String name, boolean dir, long size, String time) {
        Item it = new Item();
        it.name = name;
        it.dir = dir;
        it.size = size;
        try {
            it.mtime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(time).getTime();
        } catch (Exception e) {
            it.mtime = System.currentTimeMillis();
        }
        return it;
    }

    /* ------------------------------------------------------------- 操作 */

    private void newFolder() {
        if (!connected) {
            UI.toast(this, "还没连上");
            return;
        }
        Dialogs.prompt(this, getString(R.string.new_folder), getString(R.string.folder_name),
                "例如 backup", "", false, name -> {
                    String n = name.trim();
                    if (n.isEmpty()) return;
                    io.execute(() -> {
                        try {
                            sftp.mkdir(join(cwd, n));
                            runOnUiThread(() -> list(cwd));
                        } catch (Exception e) {
                            toastOnUi("新建失败：" + e.getMessage());
                        }
                    });
                });
    }

    private void rename(String from) {
        Dialogs.prompt(this, getString(R.string.rename), "新名称", null, from, false, name -> {
            String n = name.trim();
            if (n.isEmpty() || n.equals(from)) return;
            io.execute(() -> {
                try {
                    sftp.rename(join(cwd, from), join(cwd, n));
                    runOnUiThread(() -> list(cwd));
                } catch (Exception e) {
                    toastOnUi("重命名失败：" + e.getMessage());
                }
            });
        });
    }

    private void remove(String name, boolean dir) {
        Dialogs.confirm(this, "删除", "确定删除「" + name + "」吗？" + (dir ? "目录必须为空。" : ""),
                "删除", true, () -> io.execute(() -> {
                    try {
                        String path = join(cwd, name);
                        if (dir) sftp.rmdir(path);
                        else sftp.rm(path);
                        runOnUiThread(() -> list(cwd));
                    } catch (Exception e) {
                        toastOnUi("删除失败：" + e.getMessage());
                    }
                }));
    }

    private void download(String name, long size) {
        toastOnUi("开始下载 " + name + "（" + bytes(size) + "）");
        io.execute(() -> {
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = getFilesDir();
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                InputStream in = sftp.get(join(cwd, name));
                OutputStream os = new FileOutputStream(out);
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
                os.close();
                in.close();
                toastOnUi("已保存到 " + out.getAbsolutePath());
            } catch (Exception e) {
                toastOnUi("下载失败：" + e.getMessage());
            }
        });
    }

    private void pickUpload() {
        if (!connected) {
            UI.toast(this, "还没连上");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_UPLOAD);
        } catch (Exception e) {
            UI.toast(this, "没有可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_UPLOAD || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        String name = "upload.bin";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }

        final String fileName = name;
        toastOnUi("上传 " + fileName + " …");
        io.execute(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("读不到文件");
                sftp.put(in, join(cwd, fileName));
                in.close();
                runOnUiThread(() -> {
                    UI.toast(this, "已上传 " + fileName);
                    list(cwd);
                });
            } catch (Exception e) {
                toastOnUi("上传失败：" + e.getMessage());
            }
        });
    }

    /* ------------------------------------------------------------- 工具 */

    private static String join(String dir, String name) {
        if (dir == null || dir.isEmpty()) return "/" + name;
        if (dir.endsWith("/")) return dir + name;
        return dir + "/" + name;
    }

    static String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024.0);
        if (n < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", n / 1048576.0);
        return String.format(Locale.US, "%.2f GB", n / 1073741824.0);
    }

    private void toastOnUi(String msg) {
        runOnUiThread(() -> UI.toast(this, msg));
    }

    private void showOverlay(String msg) {
        if (overlay == null) return;
        overlay.removeAllViews();
        overlay.setVisibility(View.VISIBLE);
        ProgressBar pb = new ProgressBar(this);
        pb.getIndeterminateDrawable().setTint(UI.ACCENT);
        overlay.addView(pb, new LinearLayout.LayoutParams(UI.dp(28), UI.dp(28)));
        TextView t = UI.text(this, msg, 12f, UI.TERM_MUTED);
        t.setGravity(Gravity.CENTER);
        Widgets.top(t, 14);
        overlay.addView(t);
    }

    private void hideOverlay() {
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connected && sftp != null) list(cwd);
    }

    @Override
    protected void onDestroy() {
        connected = false;
        try {
            if (sftp != null) sftp.disconnect();
        } catch (Exception ignored) {
        }
        if (session != null) session.close();
        io.shutdownNow();
        super.onDestroy();
    }
}
