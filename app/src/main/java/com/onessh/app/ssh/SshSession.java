package com.onessh.app.ssh;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.ChannelDirectTCPIP;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 一条 SSH 连接：密码 / 公钥 / keyboard-interactive 认证，PTY shell，外加一条 SFTP 通道。
 *
 * 主机密钥走 TOFU：第一次连问一次并记住，之后指纹变了直接拒绝 —— 和网页版的行为一致。
 */
public final class SshSession {

    public interface Listener {
        void onOutput(byte[] data);

        /** state: connecting / connected / closed / error */
        void onState(String state, String message);
    }

    public interface HostKeyVerifier {
        /**
         * 在 SSH 线程上同步调用，实现方可以弹窗并阻塞等待用户选择。
         *
         * @param knownFingerprint 之前记录的指纹，首次连接为 null
         * @return true 表示信任并继续
         */
        boolean verify(String hostPort, String fingerprint, String knownFingerprint);
    }

    /** 跳板机链最多 5 级：再多基本是配错 / 环路，直接拒绝比无限连下去好。 */
    public static final int MAX_JUMPS = 5;

    public static final class Config {
        public String host;
        public int port = 22;
        public String user;
        public String password;
        public String privateKey;
        public String passphrase;
        public int cols = 80;
        public int rows = 24;
        /** 跳板机配置；为 null 表示直连（2.0.0 单跳，向后兼容） */
        public Config jump;
        /**
         * 多跳链：按“第一跳在前”的顺序。2.1.0 新增。
         * 不为空时以它为准，{@link #jump} 只保留第一跳供旧代码读；
         * 为空时回退到单跳 {@link #jump}。
         */
        public List<Config> jumps;

        /** 按“第一跳在前”返回有效的跳板机链；没有跳板机返回空列表。 */
        public List<Config> effectiveJumps() {
            if (jumps != null && !jumps.isEmpty()) return jumps;
            if (jump != null) {
                List<Config> one = new ArrayList<>();
                one.add(jump);
                return one;
            }
            return new ArrayList<>();
        }

        public String hostPort() {
            return SshSession.hostPort(host, port);
        }
    }

    /** 从保存的主机生成连接参数（含跳板机链） */
    public static Config fromHost(com.onessh.app.data.Store store,
                                  com.onessh.app.data.Host h) {
        Config c = fill(store, h);
        List<Config> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(h.id);
        List<String> ids = h.jumpChainIds();
        if (ids.size() > 1 || (ids.size() == 1 && h.jumpIds != null && !h.jumpIds.trim().isEmpty())) {
            // 2.1.0 新格式：jumpIds 已是完整链，直接按顺序解析，不再递归展开
            for (String id : ids) {
                if (chain.size() >= MAX_JUMPS) break;
                if (id == null || id.isEmpty() || !seen.add(id)) continue;
                com.onessh.app.data.Host j = store.host(id);
                if (j == null) break;
                chain.add(fill(store, j));
            }
        } else if (!ids.isEmpty()) {
            // 旧格式：只有 jumpId，沿 jumpId 链往上走（目标 → 跳板 → 跳板的跳板…），
            // 再反转成“第一跳在前”。遇环 / 缺失 / 超 5 级就停。
            List<Config> reversed = new ArrayList<>();
            String nextId = ids.get(0);
            while (nextId != null && !nextId.isEmpty() && reversed.size() < MAX_JUMPS) {
                if (!seen.add(nextId)) break;
                com.onessh.app.data.Host j = store.host(nextId);
                if (j == null) break;
                reversed.add(fill(store, j));
                nextId = j.jumpId;
            }
            for (int i = reversed.size() - 1; i >= 0; i--) chain.add(reversed.get(i));
        }
        if (!chain.isEmpty()) {
            c.jumps = chain;
            c.jump = chain.get(0);
        }
        return c;
    }

    private static Config fill(com.onessh.app.data.Store store,
                               com.onessh.app.data.Host h) {
        Config c = new Config();
        c.host = h.host;
        c.port = h.port <= 0 ? 22 : h.port;
        c.user = store.usernameFor(h);
        c.password = store.passwordFor(h);
        c.privateKey = store.privateKeyFor(h);
        c.passphrase = store.passphraseFor(h);
        return c;
    }

    private final Config cfg;
    private final Listener listener;
    private final HostKeyVerifier verifier;
    private final String hostPort;

    /** 跳板机的转发方式：优先用 SSH 的 direct-tcpip，被拒就退回在跳板机上跑 nc */
    private enum JumpMode { DIRECT, EXEC }

    private JumpMode jumpMode = JumpMode.DIRECT;
    private String jumpCommand;
    private String jumpTool;

    private JSch jsch;
    private JSch jumpJsch;
    private Session session;
    private Session jumpSession;
    /** 多跳时每一级跳板机的 Session（第一跳在前），jumpSession 恒等于最后一级。 */
    private final List<Session> jumpSessions = new ArrayList<>();
    private final List<JSch> jumpJschs = new ArrayList<>();

    /** 某一跳板机到下一跳的转发方式（direct-tcpip / 命令隧道）。 */
    private static final class HopTunnel {
        final JumpMode mode;
        final String tool;
        final String command;
        HopTunnel(JumpMode mode, String tool, String command) {
            this.mode = mode;
            this.tool = tool;
            this.command = command;
        }
    }
    private ChannelShell shell;
    private OutputStream out;
    private Thread reader;
    private Thread writer;
    private volatile boolean closed;

    /**
     * 所有要发往远端的数据都先进这个队列，由 writer 线程统一写出去。
     * 键盘输入、终端应答（DA/DSR）、窗口大小变更全在主线程上产生，
     * 直接在主线程 write 会撞 NetworkOnMainThreadException。
     */
    private final java.util.concurrent.LinkedBlockingQueue<Object> outQueue =
            new java.util.concurrent.LinkedBlockingQueue<>();

    public SshSession(Config cfg, Listener listener, HostKeyVerifier verifier) {
        this.cfg = cfg;
        this.listener = listener;
        this.verifier = verifier;
        this.hostPort = hostPort(cfg.host, cfg.port);
    }

    public static String hostPort(String host, int port) {
        String h = host == null ? "" : host.trim();
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        return port == 22 ? h : h + ":" + port;
    }

    /* ------------------------------------------------------------- 连接 */

    public void connect() {
        listener.onState("connecting", null);
        try {
            if (!cfg.effectiveJumps().isEmpty()) {
                if (cfg.effectiveJumps().size() > MAX_JUMPS) {
                    throw new JSchException("跳板机链最多 " + MAX_JUMPS + " 级，当前 "
                            + cfg.effectiveJumps().size() + " 级，请检查跳板机配置是否成环");
                }
                connectJumpChain();
            }

            jsch = new JSch();
            if (cfg.privateKey != null && !cfg.privateKey.trim().isEmpty()) {
                byte[] pass = cfg.passphrase == null || cfg.passphrase.isEmpty()
                        ? null : cfg.passphrase.getBytes(StandardCharsets.UTF_8);
                jsch.addIdentity("hostkey", cfg.privateKey.getBytes(StandardCharsets.UTF_8), null, pass);
            }
            jsch.setHostKeyRepository(new StoreHostKeys(hostPort));

            session = jsch.getSession(cfg.user, cfg.host, cfg.port);
            session.setConfig(buildConfig());
            session.setUserInfo(new Prompt());
            if (cfg.password != null) session.setPassword(cfg.password);
            if (jumpSession != null) {
                // 目标主机的 TCP 连接改走跳板机的 direct-tcpip 通道
                session.setSocketFactory(new JumpSocketFactory(jumpSession));
            }
            session.setServerAliveInterval(30000);
            session.setServerAliveCountMax(3);
            session.connect(20000);

            shell = (ChannelShell) session.openChannel("shell");
            shell.setPtyType("xterm-256color", cfg.cols, cfg.rows, cfg.cols * 8, cfg.rows * 16);
            InputStream in = shell.getInputStream();
            out = shell.getOutputStream();
            shell.connect(15000);

            listener.onState("connected", null);

            writer = new Thread(this::pumpOut, "ssh-writer");
            writer.setDaemon(true);
            writer.start();

            reader = new Thread(() -> pump(in), "ssh-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (JSchException e) {
            String msg = friendly(e);
            listener.onState("error", msg);
            close();
        } catch (Exception e) {
            String msg = clean(e.getMessage());
            // 命令隧道被对端关掉，通常是跳板机上的 nc 连不上目标
            if (jumpMode == JumpMode.EXEC && jumpTool != null
                    && (msg.contains("End of IO Stream") || msg.contains("closed by foreign")
                    || msg.contains("Connection reset"))) {
                msg = "跳板机上的 " + jumpTool + " 连不到 " + cfg.host + ":" + cfg.port
                        + "，先在跳板机执行 " + jumpTool + " -vz " + cfg.host + " " + cfg.port + " 看看";
            }
            listener.onState("error", msg);
            close();
        }
    }

    /** 先把跳板机链逐级连上，后面目标主机的 socket 从最后一级转发 */
    private void connectJumpChain() throws JSchException {
        List<Config> hops = cfg.effectiveJumps();
        Session prevSession = null;
        HopTunnel prevTunnel = null;
        for (int i = 0; i < hops.size(); i++) {
            Config hopCfg = hops.get(i);
            String nextHost = (i + 1 < hops.size()) ? hops.get(i + 1).host : cfg.host;
            int nextPort = (i + 1 < hops.size()) ? hops.get(i + 1).port : cfg.port;
            listener.onState("connecting", "正在连接跳板机 " + (i + 1) + "/" + hops.size()
                    + "（" + hopCfg.hostPort() + "）…");

            JSch hopJsch = new JSch();
            if (hopCfg.privateKey != null && !hopCfg.privateKey.trim().isEmpty()) {
                byte[] pass = hopCfg.passphrase == null || hopCfg.passphrase.isEmpty()
                        ? null : hopCfg.passphrase.getBytes(StandardCharsets.UTF_8);
                hopJsch.addIdentity("jumpkey-" + i, hopCfg.privateKey.getBytes(StandardCharsets.UTF_8), null, pass);
            }
            hopJsch.setHostKeyRepository(new StoreHostKeys(hopCfg.hostPort()));

            Session hopSession = hopJsch.getSession(hopCfg.user, hopCfg.host, hopCfg.port);
            hopSession.setConfig(buildConfig());
            hopSession.setUserInfo(new Prompt(hopCfg));
            if (hopCfg.password != null) hopSession.setPassword(hopCfg.password);
            if (prevSession != null) {
                // 这一跳的 TCP 改走上一跳的转发通道
                hopSession.setSocketFactory(new JumpSocketFactory(prevSession, prevTunnel));
            }
            hopSession.setServerAliveInterval(30000);
            try {
                hopSession.connect(20000);
            } catch (JSchException e) {
                throw new JSchException("跳板机 " + (i + 1) + "/" + hops.size()
                        + "（" + hopCfg.hostPort() + "）连不上：" + e.getMessage(), e);
            }
            jumpJschs.add(hopJsch);
            jumpSessions.add(hopSession);
            if (i == 0) jumpJsch = hopJsch;

            // 这一跳到下一跳的转发方式：先试 direct-tcpip，不行再试命令隧道
            HopTunnel tunnel = prepareTunnel(hopSession, nextHost, nextPort, i + 1, hops.size());
            prevTunnel = tunnel;
            prevSession = hopSession;
        }
        // 最后一级就是目标主机要走的跳板机，保持旧字段供目标连接 / 报错用
        jumpSession = prevSession;
        if (prevTunnel != null) {
            jumpMode = prevTunnel.mode;
            jumpTool = prevTunnel.tool;
            jumpCommand = prevTunnel.command;
        }
    }

    /** 兼容旧单跳入口：等价于只有一级的 connectJumpChain()。 */
    private void connectJump() throws JSchException {
        connectJumpChain();
    }

    /**
     * 先试 direct-tcpip；服务器不让转发（AllowTcpForwarding no）就退一步：
     * 在跳板机上跑 nc/ncat/socat，把它连出去的那条 TCP 直接当隧道用。
     * 这条路只需要跳板机有 nc，不需要 sshd 开端口转发。
     */
    private HopTunnel prepareTunnel(Session hopSession, String targetHost, int targetPort,
                                    int hopIndex, int hopCount) throws JSchException {
        if (canDirectTcpip(hopSession, targetHost, targetPort)) {
            return new HopTunnel(JumpMode.DIRECT, null, null);
        }

        listener.onState("connecting", "跳板机 " + hopIndex + "/" + hopCount
                + " 不允许端口转发，改用命令隧道…");

        String tool = detectTunnelTool(hopSession);
        if (tool == null) {
            throw new JSchException("跳板机 " + hopIndex + "/" + hopCount
                    + "（" + hopSession.getHost() + "）既不允许端口转发，也没装 nc / ncat / socat。"
                    + "可以在跳板机执行 apk add netcat-openbsd，"
                    + "或者把 sshd_config 里的 AllowTcpForwarding 改成 yes");
        }
        return new HopTunnel(JumpMode.EXEC, tool, tunnelCommand(tool, targetHost, targetPort));
    }

    private boolean canDirectTcpip(Session hopSession, String host, int port) {
        try {
            ChannelDirectTCPIP ch = (ChannelDirectTCPIP) hopSession.openChannel("direct-tcpip");
            ch.setHost(host);
            ch.setPort(port);
            ch.setOrgIPAddress("127.0.0.1");
            ch.setOrgPort(0);
            ch.connect(8000);
            ch.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 看看跳板机上有什么可用的管道工具 */
    private String detectTunnelTool(Session hopSession) {
        String out = runOnJump(hopSession, "command -v nc; command -v ncat; command -v socat");
        if (out.contains("socat")) return "socat";
        if (out.contains("ncat")) return "ncat";
        if (out.contains("nc")) return "nc";
        return null;
    }

    private static String tunnelCommand(String tool, String host, int port) {
        if ("socat".equals(tool)) return "socat - TCP:" + host + ":" + port;
        return tool + " '" + host + "' " + port;
    }

    /** 在跳板机上跑一条命令并收集输出（最多等 4 秒） */
    private String runOnJump(Session hopSession, String command) {
        ChannelExec ex = null;
        try {
            ex = (ChannelExec) hopSession.openChannel("exec");
            ex.setCommand(command);
            InputStream in = ex.getInputStream();
            ex.connect(5000);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            long deadline = System.currentTimeMillis() + 4000;
            while (System.currentTimeMillis() < deadline) {
                int avail = in.available();
                if (avail > 0) {
                    int r = in.read(buf, 0, Math.min(avail, buf.length));
                    if (r > 0) bos.write(buf, 0, r);
                } else if (ex.isClosed()) {
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        } finally {
            if (ex != null) {
                try {
                    ex.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 把 JSch 的 TCP 连接接到跳板机的 direct-tcpip 通道上。
     * JSch 只通过 getInputStream / getOutputStream 拿流，
     * 所以返回一个没真正连过的 Socket 当占位就够了。
     */
    private final class JumpSocketFactory implements SocketFactory {
        private final Session jump;
        private final HopTunnel tunnel;
        private final Socket placeholder = new Socket();
        private InputStream in;
        private OutputStream out;

        /** 目标主机走最后一级跳板机：用全局 jumpMode / jumpCommand（向后兼容）。 */
        JumpSocketFactory(Session jump) {
            this(jump, new HopTunnel(jumpMode, jumpTool, jumpCommand));
        }

        /** 多跳：每一跳走上一跳时，用上一跳自己的转发方式。 */
        JumpSocketFactory(Session jump, HopTunnel tunnel) {
            this.jump = jump;
            this.tunnel = tunnel != null ? tunnel : new HopTunnel(JumpMode.DIRECT, null, null);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            try {
                if (tunnel.mode == JumpMode.EXEC) {
                    ChannelExec ex = (ChannelExec) jump.openChannel("exec");
                    ex.setCommand(tunnel.command);
                    in = ex.getInputStream();
                    out = ex.getOutputStream();
                    ex.connect(10000);
                } else {
                    ChannelDirectTCPIP ch = (ChannelDirectTCPIP) jump.openChannel("direct-tcpip");
                    ch.setHost(host);
                    ch.setPort(port);
                    ch.setOrgIPAddress("127.0.0.1");
                    ch.setOrgPort(0);
                    ch.connect(10000);
                    in = ch.getInputStream();
                    out = ch.getOutputStream();
                }
                return placeholder;
            } catch (Exception ex) {
                String m = ex.getMessage() == null ? "" : ex.getMessage();
                if (m.contains("channel is not opened") || m.contains("open failed")) {
                    m = "跳板机连不到 " + host + ":" + port
                            + "，请在跳板机上执行 nc -vz " + host + " " + port + " 确认网络";
                }
                throw new IOException("跳板机转发失败：" + m, ex);
            }
        }

        @Override
        public InputStream getInputStream(Socket socket) throws IOException {
            if (in == null) throw new IOException("隧道未建立");
            return in;
        }

        @Override
        public OutputStream getOutputStream(Socket socket) throws IOException {
            if (out == null) throw new IOException("隧道未建立");
            return out;
        }
    }

    private Properties buildConfig() {
        Properties p = new Properties();
        p.put("StrictHostKeyChecking", "yes");
        p.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
        p.put("PubkeyAcceptedAlgorithms",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,"
                        + "rsa-sha2-512,rsa-sha2-256");
        p.put("server_host_key",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,"
                        + "rsa-sha2-512,rsa-sha2-256");
        p.put("kex", "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,"
                + "ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group16-sha512,"
                + "diffie-hellman-group14-sha256,diffie-hellman-group-exchange-sha256");
        return p;
    }

    private void pump(InputStream in) {
        byte[] buf = new byte[16384];
        try {
            int n;
            while (!closed && (n = in.read(buf)) > 0) {
                byte[] chunk = new byte[n];
                System.arraycopy(buf, 0, chunk, 0, n);
                listener.onOutput(chunk);
            }
        } catch (Exception ignored) {
        }
        if (!closed) listener.onState("closed", null);
    }

    /* ------------------------------------------------------------- 输入 */

    /** 只入队，立刻返回；真正的 write 在 writer 线程里做 */
    public void write(byte[] data) {
        if (data == null || data.length == 0 || closed) return;
        outQueue.add(data);
    }

    /** 同样入队：改窗口大小也要发一个 SSH 包出去 */
    public void resize(int cols, int rows) {
        if (cols <= 0 || rows <= 0) return;
        cfg.cols = cols;
        cfg.rows = rows;
        if (!closed) outQueue.add(new int[]{cols, rows});
    }

    private void pumpOut() {
        try {
            while (!closed) {
                Object item = outQueue.take();
                if (closed) return;
                if (item instanceof byte[]) {
                    OutputStream o = out;
                    if (o == null) continue;
                    o.write((byte[]) item);
                    o.flush();
                } else if (item instanceof int[]) {
                    int[] size = (int[]) item;
                    ChannelShell ch = shell;
                    if (ch != null) {
                        ch.setPtySize(size[0], size[1], size[0] * 8, size[1] * 16);
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            if (!closed) listener.onState("error", "写入失败：" + e.getMessage());
        }
    }

    public boolean isConnected() {
        return session != null && session.isConnected() && !closed;
    }

    /* ------------------------------------------------------------- SFTP */

    /** 打开一条 SFTP 通道；调用方负责在后台线程用，并在结束时 disconnect。 */
    public ChannelSftp openSftp() throws JSchException {
        Session s = session;
        if (s == null || !s.isConnected()) throw new JSchException("连接已断开");
        ChannelSftp sftp = (ChannelSftp) s.openChannel("sftp");
        sftp.connect(15000);
        return sftp;
    }

    public void close() {
        if (closed) return;
        closed = true;

        final ChannelShell ch = shell;
        final Session s = session;
        final Session js = jumpSession;
        // 多跳：把每一级跳板机 Session 都记下来一起断开
        final java.util.List<Session> allJumps = new java.util.ArrayList<>(jumpSessions);
        if (js != null && !allJumps.contains(js)) allJumps.add(js);
        jumpSession = null;
        jumpSessions.clear();
        jumpJschs.clear();
        shell = null;
        session = null;
        out = null;
        outQueue.clear();

        Thread w = writer;
        if (w != null) w.interrupt();

        // disconnect 也会发包，不能在主线程做
        Thread t = new Thread(() -> {
            try {
                if (ch != null) ch.disconnect();
            } catch (Exception ignored) {
            }
            try {
                if (s != null) s.disconnect();
            } catch (Exception ignored) {
            }
            for (Session hopSession : allJumps) {
                try {
                    if (hopSession != null) hopSession.disconnect();
                } catch (Exception ignored) {
                }
            }
        }, "ssh-close");
        t.setDaemon(true);
        t.start();
    }

    /* --------------------------------------------------------- 主机密钥 */

    private final class StoreHostKeys implements HostKeyRepository {

        private final String hostPort;

        StoreHostKeys(String hostPort) {
            this.hostPort = hostPort;
        }

        @Override
        public int check(String host, byte[] key) {
            String fp = fingerprint(key);
            String known = KnownHosts.known(hostPort);
            if (known == null || known.isEmpty()) {
                return verifier != null && verifier.verify(hostPort, fp, null)
                        ? OK : NOT_INCLUDED;
            }
            if (known.equals(fp)) return OK;
            return verifier != null && verifier.verify(hostPort, fp, known) ? OK : CHANGED;
        }

        @Override
        public void add(HostKey hostkey, UserInfo ui) {
            KnownHosts.trust(hostPort, fingerprintOf(hostkey.getKey()));
        }

        @Override
        public void remove(String host, String type) {
            KnownHosts.forget(hostPort);
        }

        @Override
        public void remove(String host, String type, byte[] key) {
            KnownHosts.forget(hostPort);
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return "onessh";
        }

        @Override
        public HostKey[] getHostKey() {
            return new HostKey[0];
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return new HostKey[0];
        }
    }

    /** HostKey.getKey() 给的是 base64 字符串，先解码再算摘要 */
    public static String fingerprintOf(String base64Key) {
        try {
            return fingerprint(Base64.getDecoder().decode(base64Key));
        } catch (Exception e) {
            return "SHA256:?";
        }
    }

    /** SHA256:base64 —— 和 `ssh-keygen -lf` 的输出一致 */
    public static String fingerprint(byte[] keyBlob) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(keyBlob);
            String b64 = Base64.getEncoder().encodeToString(digest);
            while (b64.endsWith("=")) b64 = b64.substring(0, b64.length() - 1);
            return "SHA256:" + b64;
        } catch (Exception e) {
            return "SHA256:?";
        }
    }

    private final class Prompt implements UserInfo, UIKeyboardInteractive {

        private final Config conf;

        Prompt() {
            this(cfg);
        }

        Prompt(Config conf) {
            this.conf = conf;
        }

        @Override
        public String getPassphrase() {
            return conf.passphrase;
        }

        @Override
        public String getPassword() {
            return conf.password;
        }

        @Override
        public boolean promptPassword(String message) {
            return conf.password != null;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return conf.passphrase != null;
        }

        @Override
        public boolean promptYesNo(String message) {
            return true;
        }

        @Override
        public void showMessage(String message) {
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name,
                                                  String instruction, String[] prompt, boolean[] echo) {
            // 二次验证（OTP 之类）在这里做不了，只能把密码填给第一问
            String[] answers = new String[prompt.length];
            for (int i = 0; i < prompt.length; i++) {
                answers[i] = (i == 0 && conf.password != null) ? conf.password : "";
            }
            return answers;
        }
    }

    /** 去掉 JSch 包在外面的 "Session.connect: java.io.IOException:" 前缀 */
    private static String clean(String m) {
        if (m == null || m.isEmpty()) return "连接失败";
        String s = m;
        int i = s.lastIndexOf("java.io.IOException: ");
        if (i >= 0) s = s.substring(i + "java.io.IOException: ".length());
        i = s.lastIndexOf("Session.connect: ");
        if (i >= 0) s = s.substring(i + "Session.connect: ".length());
        return s;
    }

    private static String friendly(JSchException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        String lower = m.toLowerCase();
        if (lower.contains("auth fail") || lower.contains("auth cancel")) return "认证失败：用户名、密码或私钥不对";
        if (lower.contains("timeout") || lower.contains("timed out")) return "连接超时：服务器没响应，检查地址和端口";
        if (lower.contains("connection refused")) return "连接被拒绝：端口没开，或 SSH 服务没运行";
        if (lower.contains("unknownhost") || lower.contains("unknown host")) return "域名解析失败";
        if (lower.contains("network is unreachable")) return "网络不可达";
        if (lower.contains("host key verification failed") || lower.contains("mismatch")) return "主机密钥不匹配，已中止连接";
        if (lower.contains("algorithm negotiation fail")) return "算法协商失败：服务器太老，不支持现代加密算法";
        if (lower.contains("invalid privatekey") || lower.contains("privatekey")) return "私钥无法解析，或需要口令";
        return m.isEmpty() ? "连接失败" : clean(m);
    }
}
