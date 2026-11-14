package com.onessh.app;

import android.content.Intent;

import java.lang.ref.WeakReference;

/**
 * 最小化会话登记：应用级，只记“哪个终端被最小化了”，不持有会话本身。
 *
 * 会话（SshSession）仍归 TerminalActivity 所有：最小化只是把 MainActivity
 * 带到前台，TerminalActivity 退到后台栈里，onDestroy 不走，会话不断。
 * MainActivity 靠这里知道要不要在右侧悬浮“返回终端”的卡片。
 * 只有一处能最小化，所以同时最多一个；切到别的主机连接前，先把旧的
 * 那个 finish 掉（onDestroy 会关会话），再开新的。
 */
public final class SessionRegistry {

    private static WeakReference<TerminalActivity> minimized;
    private static String label;
    private static Intent restoreIntent;

    private SessionRegistry() {}

    public static void minimize(TerminalActivity activity, String labelText, Intent restore) {
        minimized = new WeakReference<>(activity);
        label = labelText;
        restoreIntent = restore;
    }

    /** 只有登记的就是这个 Activity 时才清，避免误删后来的最小化 */
    public static void clearIf(TerminalActivity activity) {
        if (minimized != null && minimized.get() == activity) clear();
    }

    public static void clear() {
        minimized = null;
        label = null;
        restoreIntent = null;
    }

    /** Activity 还活着才算最小化中：系统回收掉后台页面后，登记自动失效 */
    public static boolean hasMinimized() {
        return minimized != null && minimized.get() != null;
    }

    public static TerminalActivity activity() {
        return minimized == null ? null : minimized.get();
    }

    public static String label() {
        return label == null ? "" : label;
    }

    public static Intent restoreIntent() {
        return restoreIntent;
    }
}
