package com.onessh.app.data;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 跳板机链的纯函数操作（2.2.0 新增，供 HostEditActivity 的链路编辑器用）。
 *
 * 规则跟 SshSession 保持一致：最多 5 级，第一跳在前；链里不许重复、
 * 不许把自己当跳板机；超过上限、重复、空 id 一律拒绝，不抛异常。
 * 这里不碰 Android、不碰 Store，本地单元测试可以直接跑。
 */
public final class JumpChain {

    /** 跟 SshSession.MAX_JUMPS 保持一致，改一处要两处一起改。 */
    public static final int MAX_JUMPS = 5;

    private JumpChain() {}

    /** 把 JSON 数组字符串解析成 id 列表；损坏或为空时回退到单跳 jumpId。 */
    public static List<String> parse(String jumpIds, String jumpId) {
        List<String> out = new ArrayList<>();
        if (jumpIds != null && !jumpIds.trim().isEmpty()) {
            try {
                JSONArray a = new JSONArray(jumpIds);
                for (int i = 0; i < a.length(); i++) {
                    String id = a.optString(i, "");
                    if (id != null && !id.trim().isEmpty() && !out.contains(id)) out.add(id);
                }
            } catch (Exception ignored) {
                if (jumpId != null && !jumpId.trim().isEmpty()) out.add(jumpId);
            }
            return out;
        }
        if (jumpId != null && !jumpId.trim().isEmpty()) out.add(jumpId);
        return out;
    }

    /** 列表序列化成 Host.jumpIds 用的 JSON 数组字符串；空链返回 ""。 */
    public static String toJson(List<String> chain) {
        if (chain == null || chain.isEmpty()) return "";
        JSONArray a = new JSONArray();
        for (String id : chain) {
            if (id != null && !id.trim().isEmpty()) a.put(id);
        }
        return a.length() == 0 ? "" : a.toString();
    }

    /** 追加一台：已满、重复、空 id、自己，都返回 false 且不改原列表。 */
    public static boolean add(List<String> chain, String id, String selfId) {
        if (chain == null || id == null || id.trim().isEmpty()) return false;
        if (id.equals(selfId)) return false;
        if (chain.contains(id)) return false;
        if (chain.size() >= MAX_JUMPS) return false;
        chain.add(id);
        return true;
    }

    public static boolean remove(List<String> chain, int index) {
        if (chain == null || index < 0 || index >= chain.size()) return false;
        chain.remove(index);
        return true;
    }

    /** 上移一位：已经是第一级时返回 false。 */
    public static boolean moveUp(List<String> chain, int index) {
        if (chain == null || index <= 0 || index >= chain.size()) return false;
        String id = chain.remove(index);
        chain.add(index - 1, id);
        return true;
    }

    /** 下移一位：已经是最后一级时返回 false。 */
    public static boolean moveDown(List<String> chain, int index) {
        if (chain == null || index < 0 || index >= chain.size() - 1) return false;
        String id = chain.remove(index);
        chain.add(index + 1, id);
        return true;
    }

    /** 删掉链里已经不存在的主机（编辑页打开时调用），返回删掉的数量。 */
    public static int pruneMissing(List<String> chain, List<String> existingIds) {
        if (chain == null) return 0;
        int removed = 0;
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (existingIds == null || !existingIds.contains(chain.get(i))) {
                chain.remove(i);
                removed++;
            }
        }
        return removed;
    }
}
