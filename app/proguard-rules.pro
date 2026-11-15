# OneSSH 2.1.0：Release 开 R8 时的保留规则
# JSch 用反射 / 动态加载算法类，org.json 是数据层，应用类由 Manifest 引用，
# 全部保留，避免混淆后连不上 / 解析失败。
-keep class com.jcraft.jsch.** { *; }
-keep class org.json.** { *; }
-keep class com.onessh.app.** { *; }
-dontwarn com.jcraft.jsch.**
