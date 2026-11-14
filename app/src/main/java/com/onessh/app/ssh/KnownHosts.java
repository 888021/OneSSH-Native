package com.onessh.app.ssh;

import com.onessh.app.data.Store;

/** 已信任主机密钥的存取入口（SshSession 拿不到 Context，用静态桥接）。 */
public final class KnownHosts {

    private static volatile Store store;

    private KnownHosts() {}

    public static void init(Store s) {
        store = s;
    }

    public static String known(String hostPort) {
        Store s = store;
        return s == null ? null : s.knownFingerprint(hostPort);
    }

    public static void trust(String hostPort, String fingerprint) {
        Store s = store;
        if (s != null) s.trustHost(hostPort, fingerprint);
    }

    public static void forget(String hostPort) {
        Store s = store;
        if (s != null) s.forgetHost(hostPort);
    }
}
