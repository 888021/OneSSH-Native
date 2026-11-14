package com.onessh.app.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 落盘加密。密钥由 Android Keystore 生成并保管，永远不出安全硬件/系统进程，
 * 应用卸载即失效 —— 拿到文件本身也解不开。
 */
public final class Crypto {

    private static final String ALIAS = "onessh.store.v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    private Crypto() {}

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    /** @return "v1:" + base64(iv || ciphertext)，失败返回 null */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return null;
        try {
            // IV 必须由 Keystore 自己生成：AndroidKeyStore 的密钥默认要求随机 IV，
            // 传进去的 IV 会被直接拒绝（CALLER_NONCE_PROHIBITED）。
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return "v1:" + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            android.util.Log.e("Crypto", "加密失败", e);
            return null;
        }
    }

    /** @return 明文；解不开（换了设备、密钥被清）返回 null */
    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return null;
        if (!stored.startsWith("v1:")) return null;
        try {
            byte[] all = Base64.decode(stored.substring(3), Base64.NO_WRAP);
            if (all.length <= IV_LEN) return null;
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            android.util.Log.e("Crypto", "解密失败", e);
            return null;
        }
    }
}
