package ohi.andre.consolelauncher.tuils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.managers.TerminalManager;

/**
 * Runs commands inside Termux (https://termux.dev) through its RUN_COMMAND intent,
 * so that every Linux tool installed there can be used from t-ui.
 */

public class TermuxBridge {

    public static final String PACKAGE = "com.termux";
    public static final String PERMISSION = "com.termux.permission.RUN_COMMAND";

    private static final String SERVICE = "com.termux.app.RunCommandService";
    private static final String ACTION = "com.termux.RUN_COMMAND";
    private static final String EXTRA_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    private static final String EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION";
    private static final String EXTRA_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    private static final String EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";

    private static final String RESULT_BUNDLE = "result";
    private static final String RESULT_STDOUT = "stdout";
    private static final String RESULT_STDERR = "stderr";
    private static final String RESULT_EXIT_CODE = "exitCode";
    private static final String RESULT_ERR = "err";
    private static final String RESULT_ERRMSG = "errmsg";

    private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String HOME = "/data/data/com.termux/files/home";

    public static final String SETUP_COMMAND = "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings";

//    executed by the Android shell even though they are not files in PATH
    private static final Set<String> SHELL_BUILTINS = new HashSet<>(Arrays.asList(
            "cd", "echo", "export", "alias", "unalias", "set", "unset", "read", "test", "[", "type", "command",
            "exit", "kill", "pwd", "true", "false", "source", ".", "eval", "exec", "ulimit", "umask", "wait",
            "jobs", "fg", "bg", "trap", "let", "print", "typeset", "local", "whence", "getopts", "shift",
            "times", "return", "break", "continue", "builtin", "printf", "if", "for", "while", "until",
            "case", "function", "{", "(", "!", "su"));

//    full screen / interactive programs: they need a real terminal, so they open in a Termux window
    private static final Set<String> INTERACTIVE = new HashSet<>(Arrays.asList(
            "vim", "vi", "nvim", "nano", "emacs", "micro", "htop", "btop", "top", "less", "more", "man",
            "mc", "tmux", "screen", "bash", "zsh", "fish", "ranger", "nnn", "w3m", "lynx", "mutt", "ncdu",
            "cmatrix", "ipython", "irssi", "weechat", "telnet", "ftp", "sftp", "mosh"));
    private static final Set<String> REPL_WITHOUT_ARGS = new HashSet<>(Arrays.asList(
            "python", "python3", "node", "irb", "lua", "php", "ruby", "perl", "R", "sqlite3", "ghci"));

    private static final AtomicInteger requestCodes = new AtomicInteger(1000);

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

//    true if Android itself can run this command (builtin, path, or binary in PATH)
    public static boolean isAndroidCommand(String commandLine) {
        String cmd = firstWord(commandLine);
        if (cmd.length() == 0 || cmd.contains("/") || cmd.contains("=") || SHELL_BUILTINS.contains(cmd)) return true;

        String path = System.getenv("PATH");
        if (path == null) path = "/system/bin:/system/xbin:/vendor/bin";
        for (String dir : path.split(":")) {
            if (new File(dir, cmd).exists()) return true;
        }
        return false;
    }

    public static boolean isInteractive(String commandLine) {
        String[] words = commandLine.trim().split("\\s+");
        if (words.length == 0) return false;
        if (INTERACTIVE.contains(words[0])) return true;
        return words.length == 1 && REPL_WITHOUT_ARGS.contains(words[0]);
    }

    private static String firstWord(String commandLine) {
        String trimmed = commandLine.trim();
        int space = trimmed.indexOf(' ');
        return space == -1 ? trimmed : trimmed.substring(0, space);
    }

    /**
     * @return a message to show, or null when the command was sent
     */
    public static String run(Context context, String commandLine, boolean openTerminal) {
        if (!isInstalled(context)) {
            return "Termux is not installed. Install it from F-Droid (f-droid.org/packages/com.termux), then type: termux -setup";
        }

        if (!hasPermission(context)) {
//            LauncherActivity runs the last command again once the permission is granted
            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context, new String[] {PERMISSION}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
                return null;
            }
            return "T-UI is not allowed to use Termux yet. Type: termux -setup";
        }

        Intent intent = new Intent(ACTION);
        intent.setClassName(PACKAGE, SERVICE);
        intent.putExtra(EXTRA_PATH, BASH);
        intent.putExtra(EXTRA_ARGUMENTS, new String[] {"-lc", commandLine});
        intent.putExtra(EXTRA_WORKDIR, HOME);
        intent.putExtra(EXTRA_LABEL, commandLine);

        if (openTerminal) {
            intent.putExtra(EXTRA_BACKGROUND, false);
//            0: open a new session and show it
            intent.putExtra(EXTRA_SESSION_ACTION, "0");
        } else {
            intent.putExtra(EXTRA_BACKGROUND, true);

            Intent result = new Intent(context, ResultReceiver.class);
//            Termux fills the result extras, so the PendingIntent has to be mutable
            PendingIntent pending = PendingIntent.getBroadcast(context.getApplicationContext(), requestCodes.incrementAndGet(), result,
                    Tuils.mutablePendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT));
            intent.putExtra(EXTRA_PENDING_INTENT, pending);
        }

        try {
            context.startService(intent);
        } catch (Exception e) {
            return "Termux refused the command (" + e.getMessage() + "). Type: termux -setup";
        }

        if (openTerminal) {
//            Termux needs "display over other apps" to come to the front by itself: open it from here instead
            Intent open = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (open != null) {
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(open);
                } catch (Exception e) {
                    Tuils.log(e);
                }
            }
        }
        return null;
    }

    public static String status(Context context) {
        StringBuilder b = new StringBuilder();
        b.append("Termux installed: ").append(isInstalled(context) ? "yes" : "no").append(Tuils.NEWLINE);
        b.append("Permission granted to t-ui: ").append(hasPermission(context) ? "yes" : "no");
        return b.toString();
    }

    public static class ResultReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle result = intent.getBundleExtra(RESULT_BUNDLE);
            if (result == null) return;

            String stdout = result.getString(RESULT_STDOUT, Tuils.EMPTYSTRING);
            String stderr = result.getString(RESULT_STDERR, Tuils.EMPTYSTRING);
            int exitCode = result.getInt(RESULT_EXIT_CODE, 0);
            int err = result.getInt(RESULT_ERR, Activity.RESULT_OK);
            String errmsg = result.getString(RESULT_ERRMSG);

            if (err != Activity.RESULT_OK) {
                Tuils.sendOutput(Color.RED, context, "Termux error: " + (errmsg != null ? errmsg : err)
                        + Tuils.NEWLINE + "If external apps are not allowed, type in Termux: " + SETUP_COMMAND);
                return;
            }

            if (stdout != null && stdout.trim().length() > 0) {
                Tuils.sendOutput(context, trimEnd(stdout), TerminalManager.CATEGORY_OUTPUT);
            }
            if (stderr != null && stderr.trim().length() > 0) {
                Tuils.sendOutput(context, trimEnd(stderr), TerminalManager.CATEGORY_OUTPUT);
            }
            if (exitCode == 127 && (stderr == null || stderr.contains("not found"))) {
                Tuils.sendOutput(context, "Not installed in Termux either. Install it with: pkg install <package>", TerminalManager.CATEGORY_OUTPUT);
            } else if (exitCode != 0) {
                Tuils.sendOutput(context, "[exit " + exitCode + "]", TerminalManager.CATEGORY_OUTPUT);
            }
        }

        private static String trimEnd(String s) {
            int end = s.length();
            while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
            return s.substring(0, end);
        }
    }
}
