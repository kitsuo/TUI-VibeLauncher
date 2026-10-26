package ohi.andre.consolelauncher.tuils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Ui;

/**
 * What happens when t-ui starts (once per process, not on every reload):
 * the BIOS-like boot screen (fx boot on/off), then the commands of the startup script (.tuirc).
 */

public class BootSequence {

    public static final String RC_FILE = ".tuirc";
    private static final int LINE_DELAY = 110;

    private static boolean done = false;

    public static void onStart(Context context, MainManager main) {
        if (done) return;
        done = true;

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable script = () -> runScript(main);

        if (!XMLPrefsManager.getBoolean(Ui.boot_animation)) {
            handler.postDelayed(script, 300);
            return;
        }

        List<String> lines = bootLines(context);
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            handler.postDelayed(() -> Tuils.sendOutput(context, line, TerminalManager.CATEGORY_OUTPUT), 300 + (long) i * LINE_DELAY);
        }
        handler.postDelayed(script, 300 + (long) lines.size() * LINE_DELAY + 200);
    }

    private static List<String> bootLines(Context context) {
        List<String> l = new ArrayList<>();
        l.add("T-UI BIOS " + BuildConfig.VERSION_NAME);
        l.add(Build.MANUFACTURER.toUpperCase(Locale.ROOT) + " " + Build.MODEL + " - Android " + Build.VERSION.RELEASE);

        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Build.SOC_MODEL : Build.HARDWARE;
        l.add(ok("CPU " + soc + " x" + Runtime.getRuntime().availableProcessors()));

        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mem);
            l.add(ok(String.format(Locale.ROOT, "Memory test %.0f MB", mem.totalMem / 1024.0 / 1024.0)));
        } catch (Exception ignored) {}

        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            l.add(ok(String.format(Locale.ROOT, "Storage %.0f GB, %.0f GB free", fs.getTotalBytes() / 1e9, fs.getAvailableBytes() / 1e9)));
        } catch (Exception ignored) {}

        NetTools.LocalAddress ip = NetTools.lanAddress();
        l.add(ip != null ? ok("Network " + ip.iface + " " + ip.address.getHostAddress()) : fail("Network"));

        try {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            l.add(ok("Battery " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + "%"));
        } catch (Exception ignored) {}

        l.add(ok("Loading shell"));
        File rc = new File(Tuils.getFolder(), RC_FILE);
        if (rc.exists()) l.add(ok("Running " + RC_FILE));
        l.add("Ready.");
        return l;
    }

//    "Memory test 8192 MB ......... [ OK ]"
    private static String ok(String s) {
        return pad(s) + "[ OK ]";
    }

    private static String fail(String s) {
        return pad(s) + "[FAIL]";
    }

    private static String pad(String s) {
        StringBuilder b = new StringBuilder(s).append(' ');
        while (b.length() < 30) b.append('.');
        return b.append(' ').toString();
    }

//    one command per line, # for comments
    private static void runScript(MainManager main) {
        File rc = new File(Tuils.getFolder(), RC_FILE);
        if (!rc.exists()) return;

        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(rc)))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                main.onCommand(line, (String) null, false);
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    public static File rcFile() {
        File rc = new File(Tuils.getFolder(), RC_FILE);
        if (!rc.exists()) {
            try (java.io.Writer w = new java.io.FileWriter(rc)) {
                w.write("# T-UI startup script: one command per line, run when T-UI starts\n"
                        + "# (not when it reloads after a setting change). Lines starting with # are ignored.\n"
                        + "# Examples:\n"
                        + "# fetch\n"
                        + "# ssh srv uptime\n"
                        + "# notif list\n");
            } catch (Exception e) {
                Tuils.log(e);
            }
        }
        return rc;
    }
}
