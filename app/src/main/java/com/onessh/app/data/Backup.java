package com.onessh.app.data;

/**
 * 备份文件格式（2.2.0 新增，纯函数，不碰 Android）。
 *
 * 导出文件是带标记的 JSON：
 * {@code {"format":"onessh-backup","version":1,"data":{...原 onessh.json 内容...}}}
 *
 * 注意：data 里的密码 / 私钥仍是 Keystore 加密后的密文（"v1:..."），
 * 换了设备 / 清了 Keystore 就解不开，导入后要重填密码和私钥；
 * 主机、分组、凭据名、片段、已信任指纹这些不受影响。
 * 所以这份备份可以放心存到自己手里，但不要发给别人。
 */
public final class Backup {

    public static final String FORMAT = "onessh-backup";
    public static final int VERSION = 1;

    private Backup() {}

    /** 把原库 JSON 包一层标记；原内容为空时返回 null。 */
    public static String wrap(String storeJson) {
        if (storeJson == null || storeJson.trim().isEmpty()) return null;
        return "{\"format\":\"" + FORMAT + "\",\"version\":" + VERSION
                + ",\"data\":" + storeJson.trim() + "}";
    }

    /**
     * 从备份文件里取出 data 部分的 JSON；不是备份格式时返回 null。
     * 兼容两种输入：带标记的备份文件，和直接拿原 onessh.json 当备份的老文件。
     */
    public static String unwrap(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        if (!t.contains("\"" + FORMAT + "\"")) {
            // 没标记：当作原库 JSON 直接用（老备份兼容）
            return t.startsWith("{") ? t : null;
        }
        int dataAt = t.indexOf("\"data\"");
        if (dataAt < 0) return null;
        int brace = t.indexOf('{', dataAt + 6);
        if (brace < 0) return null;
        int end = matchingBrace(t, brace);
        if (end < 0) return null;
        return t.substring(brace, end + 1);
    }

    /** 找到与 start 处 '{' 配对的 '}'，跳过字符串里的花括号。 */
    private static int matchingBrace(String s, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
