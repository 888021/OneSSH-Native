package com.onessh.app.data;

import org.json.JSONObject;

/** 一条代码片段：名字 + 要发送/复制的命令。 */
public class Snippet {

    public String id = "";
    public String name = "";
    public String command = "";
    public long createdAt;

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("command", command);
            o.put("createdAt", createdAt);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Snippet fromJson(JSONObject o) {
        Snippet s = new Snippet();
        s.id = o.optString("id", "");
        s.name = o.optString("name", "");
        s.command = o.optString("command", "");
        s.createdAt = o.optLong("createdAt", 0);
        return s;
    }
}
