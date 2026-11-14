package com.onessh.app.ssh;

import android.app.Activity;
import android.app.Dialog;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.onessh.app.R;
import com.onessh.app.ui.Dialogs;
import com.onessh.app.ui.UI;
import com.onessh.app.ui.Widgets;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 主机密钥确认弹窗。JSch 在 SSH 线程上同步调用 HostKeyRepository，
 * 所以这里负责「弹窗 + 阻塞」，把用户的选择带回去。
 */
public final class HostKeyPrompt {

    private HostKeyPrompt() {}

    /**
     * @param known 之前记录的指纹；首次连接传 null
     * @return true 表示信任并继续
     */
    public static boolean ask(final Activity activity, final String hostPort,
                              final String fingerprint, final String known) {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] accepted = {false};

        activity.runOnUiThread(() -> {
            boolean changed = known != null && !known.isEmpty();

            LinearLayout box = UI.column(activity);
            box.addView(UI.text(activity, "主机 " + hostPort, 11.5f, UI.MUTED));

            TextView fp = UI.mono(activity, fingerprint, 11.5f, UI.INK);
            fp.setBackground(UI.bg(UI.PAPER2, 8f, UI.LINE, 1f));
            fp.setPadding(UI.dp(12), UI.dp(11), UI.dp(12), UI.dp(11));
            fp.setTextIsSelectable(true);
            Widgets.top(fp, 8);
            box.addView(fp);

            if (changed) {
                TextView oldLabel = UI.text(activity, "之前的指纹", 11f, UI.MUTED);
                Widgets.top(oldLabel, 14);
                box.addView(oldLabel);

                TextView old = UI.mono(activity, known, 11f, UI.RED);
                old.setBackground(UI.bg(0xFFFFF3F1, 8f, 0xFFF0D5D1, 1f));
                old.setPadding(UI.dp(12), UI.dp(9), UI.dp(12), UI.dp(9));
                Widgets.top(old, 6);
                box.addView(old);
            }

            TextView note = UI.text(activity, changed
                            ? "指纹变化通常意味着服务器被重装，也可能意味着中间人攻击。如果不是你自己改的，请立刻断开。"
                            : "第一次连接这台服务器。核对指纹无误后再建立连接，之后会自动记住。",
                    11.5f, UI.MUTED);
            note.setLineSpacing(UI.dp(3), 1f);
            Widgets.top(note, 14);
            box.addView(note);

            Dialog dlg = Dialogs.custom(activity,
                    changed ? activity.getString(R.string.hostkey_changed_title)
                            : activity.getString(R.string.hostkey_title),
                    null, box,
                    changed ? "仍然信任" : activity.getString(R.string.trust_and_connect),
                    activity.getString(R.string.abort), changed, () -> {
                        KnownHosts.trust(hostPort, fingerprint);
                        accepted[0] = true;
                    });
            dlg.setOnDismissListener(x -> latch.countDown());
        });

        try {
            if (!latch.await(180, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            return false;
        }
        return accepted[0];
    }
}
