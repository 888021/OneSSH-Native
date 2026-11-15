package com.onessh.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * SSH 会话前台服务（2.2.0 新增）。
 *
 * 作用只有一个：终端连上之后，让系统把进程当成“正在前台干活”，
 * 降低切到后台 / 锁屏后被回收的概率，并给用户一个“正在连接”的通知，
 * 点通知可以回到终端。
 *
 * 注意：会话（SshSession）仍归 TerminalActivity 所有，服务不持有会话、
 * 不替 Activity 保活到进程被杀之后。真走到 onDestroy，会话还是会关。
 * 这一步解决的是“切后台就被回收”的常见情况，不是“杀进程也不断”。
 */
public class SshForegroundService extends Service {

    public static final String ACTION_START = "com.onessh.app.START_SESSION";
    public static final String ACTION_STOP = "com.onessh.app.STOP_SESSION";
    public static final String EXTRA_LABEL = "label";

    private static final String CHANNEL_ID = "onessh-session";
    private static final int NOTIFICATION_ID = 1001;

    /** 终端连上后调用；label 显示在通知里。 */
    public static void start(Context ctx, String label) {
        Intent i = new Intent(ctx, SshForegroundService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_LABEL, label == null ? "OneSSH" : label);
        try {
            ctx.startForegroundService(i);
        } catch (Exception ignored) {
            // 极少数机型 / 后台启动限制下起不来也不影响终端本身
        }
    }

    /** 会话结束 / 页面销毁时调用。 */
    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, SshForegroundService.class);
        i.setAction(ACTION_STOP);
        try {
            ctx.startService(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        String label = intent == null ? "OneSSH" : intent.getStringExtra(EXTRA_LABEL);
        if (label == null || label.isEmpty()) label = "OneSSH";
        try {
            startForeground(NOTIFICATION_ID, buildNotification(label));
        } catch (Exception ignored) {
            // Android 13+ 没给通知权限等情况下，服务起不来也不崩
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String label) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "SSH 会话", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("终端连接中时保持会话不被系统回收");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        // 点通知回到终端：用 MainActivity 的入口，再由悬浮卡片回终端
        Intent back = new Intent(this, MainActivity.class);
        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                this, 0, back, android.app.PendingIntent.FLAG_IMMUTABLE
                        | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        return b.setContentTitle("OneSSH 已连接：" + label)
                .setContentText("终端会话运行中，点此回到 OneSSH")
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
