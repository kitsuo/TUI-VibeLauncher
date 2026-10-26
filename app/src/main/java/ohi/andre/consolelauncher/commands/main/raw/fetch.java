package ohi.andre.consolelauncher.commands.main.raw;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * fetch: system summary, neofetch style
 */

public class fetch implements CommandAbstraction {

    private static final int ANDROID_GREEN = 0xff3ddc84;

    private static final String[] LOGO = {
            "   \\  .--.  /",
            "    .'    '.",
            "   /  o  o  \\",
            "  |__________|",
            " [|          |]",
            " [|          |]",
            "  |__________|",
            "     |_| |_|",
    };

    @Override
    public String exec(ExecutePack pack) throws Exception {
        Context c = pack.context;
        int keyColor = XMLPrefsManager.getColor(Theme.input_color);
        int textColor = XMLPrefsManager.getColor(Theme.output_color);

        SpannableStringBuilder b = new SpannableStringBuilder();
        for (String line : LOGO) append(b, line + "\n", ANDROID_GREEN);

        String user = XMLPrefsManager.get(Ui.username);
        String device = XMLPrefsManager.get(Ui.deviceName);
        if (device == null || device.length() == 0) device = Build.DEVICE;
        String title = (user != null && user.length() > 0 ? user : "user") + "@" + device;
        append(b, "\n" + title + "\n", keyColor);
        append(b, repeat('-', title.length()) + "\n", textColor);

        String oneUi = prop("ro.build.version.oneui");
        line(b, "Phone", Build.MANUFACTURER.substring(0, 1).toUpperCase() + Build.MANUFACTURER.substring(1) + " " + Build.MODEL, keyColor, textColor);
        line(b, "OS", "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" + oneUi(oneUi), keyColor, textColor);
        line(b, "Kernel", System.getProperty("os.version"), keyColor, textColor);
        line(b, "Uptime", uptime(SystemClock.elapsedRealtime() / 1000), keyColor, textColor);
        line(b, "Launcher", "T-UI " + BuildConfig.VERSION_NAME, keyColor, textColor);
        line(b, "CPU", cpu(), keyColor, textColor);

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        line(b, "RAM", gb(mem.totalMem - mem.availMem) + " / " + gb(mem.totalMem), keyColor, textColor);

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
        line(b, "Storage", gb(total - free) + " / " + gb(total), keyColor, textColor);

        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            boolean charging = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bm.isCharging();
            line(b, "Battery", level + "%" + (charging ? " (charging)" : ""), keyColor, textColor);
        }

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        line(b, "Screen", Math.max(dm.widthPixels, dm.heightPixels) + "x" + Math.min(dm.widthPixels, dm.heightPixels) + " @ " + dm.densityDpi + "dpi", keyColor, textColor);

        NetTools.LocalAddress ip = NetTools.lanAddress();
        if (ip != null) line(b, "IP", ip.address.getHostAddress() + " (" + ip.iface + ")", keyColor, textColor);

        if (pack instanceof MainPack && ((MainPack) pack).appsManager != null) {
            line(b, "Apps", String.valueOf(((MainPack) pack).appsManager.shownApps().size()), keyColor, textColor);
        }

//        the color palette, like neofetch
        b.append("\n");
        int[] palette = {0xff000000, 0xffff5252, 0xff27ae60, 0xfff1c40f, 0xff2196f3, 0xff9c27b0, 0xff00bcd4, 0xffffffff};
        for (int color : palette) append(b, "██", color);

        //        already colored: the output color must not cover the logo and the palette
        Tuils.sendOutput(c, b, TerminalManager.CATEGORY_NO_COLOR);
        return null;
    }

    private static void line(SpannableStringBuilder b, String key, String value, int keyColor, int textColor) {
        append(b, key + ": ", keyColor);
        append(b, value + "\n", textColor);
    }

    private static void append(SpannableStringBuilder b, String text, int color) {
        int start = b.length();
        b.append(text);
        b.setSpan(new ForegroundColorSpan(color), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String repeat(char c, int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) s.append(c);
        return s.toString();
    }

//    90000 -> "1 day, 1 hour" ; 3700 -> "1 hour, 1 min"
    private static String uptime(long seconds) {
        long days = seconds / 86400, hours = (seconds / 3600) % 24, mins = (seconds / 60) % 60;
        StringBuilder s = new StringBuilder();
        if (days > 0) s.append(days).append(days == 1 ? " day, " : " days, ");
        if (hours > 0 || days > 0) s.append(hours).append(hours == 1 ? " hour, " : " hours, ");
        s.append(mins).append(" min");
        return s.toString();
    }

    private static String gb(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private static String cpu() {
        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.SOC_MODEL : null;
        if (soc == null || soc.length() == 0 || soc.equals(Build.UNKNOWN)) soc = Build.HARDWARE;
        return soc + " (" + Runtime.getRuntime().availableProcessors() + " cores)";
    }

//    One UI 7.0 is stored as 70000
    private static String oneUi(String value) {
        try {
            int v = Integer.parseInt(value.trim());
            if (v <= 0) return "";
            return ", One UI " + (v / 10000) + "." + ((v / 100) % 100);
        } catch (Exception e) {
            return "";
        }
    }

    private static String prop(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"getprop", name});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = r.readLine();
                return line != null ? line : "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int[] argType() {
        return new int[0];
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_fetch;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return null;
    }
}
