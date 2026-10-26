package ohi.andre.consolelauncher.tuils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ctrl+C for the commands started by t-ui: sends SIGINT to every process run by our shells
 * (ping, top, find...), and SIGKILL to the ones that are still alive one second later.
 * The shells themselves are kept, so the next commands work as usual.
 */

public class ProcessInterrupter {

    public static final String CTRL_C = "^c";
    public static final String CTRL_C_ALT = "~c";

    private static final int SIGINT = 2;
    private static final int SIGKILL = 9;
    private static final long KILL_DELAY_MS = 1000;

    public static boolean isCtrlC(String input) {
        if (input == null) return false;
        String s = input.trim();
        return s.equalsIgnoreCase(CTRL_C) || s.equalsIgnoreCase(CTRL_C_ALT);
    }

    /**
     * @return the number of processes interrupted
     */
    public static int interrupt() {
        List<Integer> targets = runningCommands();
        for (int pid : targets) android.os.Process.sendSignal(pid, SIGINT);

        if (targets.size() > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(KILL_DELAY_MS);
                } catch (InterruptedException e) {
                    return;
                }
                for (int pid : targets) {
                    if (new File("/proc/" + pid).exists()) android.os.Process.sendSignal(pid, SIGKILL);
                }
            }).start();
        }
        return targets.size();
    }

//    the descendants of our shells (children of t-ui itself), except "su": killing it would end the root shell
    private static List<Integer> runningCommands() {
        int me = android.os.Process.myPid();

        Map<Integer, List<Integer>> children = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();

//        since Android 7 /proc only shows our own processes, which are the ones we want
        File[] procs = new File("/proc").listFiles();
        if (procs == null) return new ArrayList<>();

        for (File p : procs) {
            int pid;
            try {
                pid = Integer.parseInt(p.getName());
            } catch (NumberFormatException e) {
                continue;
            }

            String stat = read(new File(p, "stat"));
            if (stat == null) continue;

//            pid (name) state ppid ...    the name can contain spaces and parentheses
            int open = stat.indexOf('('), close = stat.lastIndexOf(')');
            if (open == -1 || close == -1 || close + 2 >= stat.length()) continue;
            String[] fields = stat.substring(close + 2).split(" ");
            if (fields.length < 2) continue;

            try {
                int ppid = Integer.parseInt(fields[1]);
                List<Integer> list = children.get(ppid);
                if (list == null) children.put(ppid, list = new ArrayList<>());
                list.add(pid);
                names.put(pid, stat.substring(open + 1, close));
            } catch (NumberFormatException ignored) {}
        }

        List<Integer> result = new ArrayList<>();
        List<Integer> shells = children.get(me);
        if (shells == null) return result;

        List<Integer> queue = new ArrayList<>();
        for (int shell : shells) {
            List<Integer> c = children.get(shell);
            if (c != null) queue.addAll(c);
        }

        while (queue.size() > 0) {
            int pid = queue.remove(queue.size() - 1);
            if ("su".equals(names.get(pid))) continue;

            result.add(pid);
            List<Integer> c = children.get(pid);
            if (c != null) queue.addAll(c);
        }
        return result;
    }

    private static String read(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buffer = new byte[512];
            int n = in.read(buffer);
            return n > 0 ? new String(buffer, 0, n) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
