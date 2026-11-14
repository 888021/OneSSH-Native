package com.onessh.app.data;

import org.json.JSONObject;

/** 凭据库里的常用账号：一组「用户名 + 密码」，可以给多台主机复用。 */
public class Credential {

    public String id = "";
    public String name = "";
    public String username = "";
    public String passwordEnc = "";
    public long createdAt;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("username", username);
            o.put("passwordEnc", passwordEnc);
            o.put("createdAt", createdAt);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Credential fromJson(JSONObject o) {
        Credential c = new Credential();
        c.id = o.optString("id", "");
        c.name = o.optString("name", "");
        c.username = o.optString("username", "");
        c.passwordEnc = o.optString("passwordEnc", "");
        c.createdAt = o.optLong("createdAt", 0);
        return c;
    }
}
