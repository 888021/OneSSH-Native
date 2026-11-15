package com.onessh.app.ssh;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SshSession 纯函数测试：指纹、主机端口格式化、多跳链展开。
 * 不连网、不需要 Android / 真机；fromHost() 的 Store 部分不在这里测。
 */
public class SshSessionTest {

    @Test
    public void hostPortOmitsDefaultPort() {
        assertEquals("example.com", SshSession.hostPort("example.com", 22));
        assertEquals("example.com:2222", SshSession.hostPort("example.com", 2222));
    }

    @Test
    public void hostPortStripsIpv6Brackets() {
        assertEquals("::1", SshSession.hostPort("[::1]", 22));
        assertEquals("::1:2222", SshSession.hostPort("[::1]", 2222));
    }

    @Test
    public void fingerprintMatchesSshKeygenSha256() {
        // SHA-256("") 的 base64（去垫码），与 `ssh-keygen -lf` 的 SHA256: 格式一致
        assertEquals("SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU",
                SshSession.fingerprint(new byte[0]));
        assertEquals("SHA256:LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ",
                SshSession.fingerprint("hello".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void fingerprintOfDecodesBase64First() {
        assertEquals("SHA256:LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ",
                SshSession.fingerprintOf("aGVsbG8="));
        assertEquals("SHA256:?", SshSession.fingerprintOf("!!!not-base64!!!"));
    }

    @Test
    public void effectiveJumpsPrefersJumpListOverSingleJump() {
        SshSession.Config target = new SshSession.Config();
        SshSession.Config hop1 = new SshSession.Config();
        hop1.host = "hop1.example.com";
        SshSession.Config hop2 = new SshSession.Config();
        hop2.host = "hop2.example.com";
        target.jumps = Arrays.asList(hop1, hop2);
        target.jump = hop1; // 旧字段只保留第一跳
        assertEquals(2, target.effectiveJumps().size());
        assertEquals("hop1.example.com", target.effectiveJumps().get(0).host);
        assertEquals("hop2.example.com", target.effectiveJumps().get(1).host);
    }

    @Test
    public void effectiveJumpsFallsBackToSingleJump() {
        SshSession.Config target = new SshSession.Config();
        SshSession.Config hop = new SshSession.Config();
        hop.host = "jump.example.com";
        target.jump = hop;
        assertEquals(1, target.effectiveJumps().size());
        assertEquals("jump.example.com", target.effectiveJumps().get(0).host);
    }

    @Test
    public void effectiveJumpsIsEmptyForDirectConnection() {
        SshSession.Config target = new SshSession.Config();
        target.host = "direct.example.com";
        assertTrue(target.effectiveJumps().isEmpty());
    }

    @Test
    public void maxJumpsIsFive() {
        assertEquals(5, SshSession.MAX_JUMPS);
    }
}
