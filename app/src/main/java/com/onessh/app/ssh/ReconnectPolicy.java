package com.onessh.app.ssh;

/**
 * 自动重连策略（2.2.0 新增，纯函数，不碰 Android）。
 *
 * 意外断开时最多自动重试 3 次，间隔 3s / 6s / 12s 退避；
 * 用户主动断开、点了“返回”、或已经试满 3 次，就不再重试。
 */
public final class ReconnectPolicy {

    public static final int MAX_ATTEMPTS = 3;
    private static final long[] DELAYS_MS = {3000L, 6000L, 12000L};

    private ReconnectPolicy() {}

    /** 第 attempt 次（从 1 开始）重试前要等多久；超过上限返回 -1。 */
    public static long delayForAttempt(int attempt) {
        if (attempt < 1 || attempt > MAX_ATTEMPTS) return -1;
        return DELAYS_MS[attempt - 1];
    }

    /** 是否应该自动重试：不是用户主动断开，且还没试满。 */
    public static boolean shouldRetry(boolean userClosed, int attemptsUsed) {
        return !userClosed && attemptsUsed < MAX_ATTEMPTS;
    }
}
