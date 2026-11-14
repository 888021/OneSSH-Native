package com.onessh.app.data;

import org.json.JSONObject;

/** 私钥库条目：私钥正文和口令都是密文，算法与指纹只用于展示。 */
public class KeyItem {

    public String id = "";
    public String name = "";
    public String algo = "";
    public String fingerprint = "";
    public String keyEnc = "";
    public String passphraseEnc = "";
    public long createdAt;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("algo", algo);
            o.put("fingerprint", fingerprint);
            o.put("keyEnc", keyEnc);
            o.put("passphraseEnc", passphraseEnc);
            o.put("createdAt", createdAt);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static KeyItem fromJson(JSONObject o) {
        KeyItem k = new KeyItem();
        k.id = o.optString("id", "");
        k.name = o.optString("name", "");
        k.algo = o.optString("algo", "");
        k.fingerprint = o.optString("fingerprint", "");
        k.keyEnc = o.optString("keyEnc", "");
        k.passphraseEnc = o.optString("passphraseEnc", "");
        k.createdAt = o.optLong("createdAt", 0);
        return k;
    }
}
