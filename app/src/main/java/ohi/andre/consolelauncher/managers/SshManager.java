package ohi.andre.consolelauncher.managers;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Line-based ssh client living inside the t-ui terminal.
 * While a session is open, every line typed in t-ui is sent to the remote shell.
 */

public class SshManager {

    public static final String DISCONNECT = "~.";
    public static final String CTRL_C = "~c";
    public static final String CTRL_D = "~d";

    private static final int CONNECT_TIMEOUT = 20000;
    private static final int KEEPALIVE_INTERVAL = 30000;
    private static final int ANSWER_TIMEOUT_MINUTES = 3;
//    after this much silence, a line without '\n' (a prompt) is shown
    private static final int PARTIAL_LINE_DELAY = 150;

    private static final Pattern ANSI = Pattern.compile(
            "\u001B\\[[0-?]*[ -/]*[@-~]"          // CSI: colors, cursor moves...
            + "|\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)"  // OSC: window title...
            + "|\u001B[()][0-9A-Za-z]"            // charset selection
            + "|\u001B[=>78DEHMc]");              // misc single-char escapes
    private static final Pattern SHELL_PROMPT = Pattern.compile(".*[$#%>]\\s?$");
    private static final Pattern SECRET_PROMPT = Pattern.compile("(?i).*(password|passphrase|mot de passe|verification code|pin)[^\\n]*[:?]\\s*$");

    static {
//        Android has no Ed25519/X25519 in its crypto provider: always use the Bouncy Castle implementations
        JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519");
        JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448");
        JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH");
        JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA");
        JSch.setConfig("mlkem768", "com.jcraft.jsch.bc.MLKEM768");
        JSch.setConfig("mlkem1024", "com.jcraft.jsch.bc.MLKEM1024");
    }

    private static volatile SshManager current;

    public static boolean isActive() {
        return current != null;
    }

    public static boolean isSecretInputExpected() {
        SshManager c = current;
        return c != null && c.secretExpected;
    }

    public static void onInput(String line) {
        SshManager c = current;
        if (c != null) c.handleInput(line);
    }

//    folder holding known_hosts and the default key pair
    public static File sshFolder() {
        File f = new File(Tuils.getFolder(), "ssh");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File defaultKey() {
        return new File(sshFolder(), "id_ed25519");
    }

    private static File knownHosts() {
        File f = new File(sshFolder(), "known_hosts");
        try {
            if (!f.exists()) f.createNewFile();
        } catch (Exception e) {
            Tuils.log(e);
        }
        return f;
    }

    // ---------------------------------------------------------------- key management

    public static String generateKey(Context context) {
        File key = defaultKey();
        if (key.exists()) {
            return "A key already exists. Public key:" + Tuils.NEWLINE + publicKey();
        }

        try {
            JSch jsch = new JSch();
            KeyPair pair = KeyPair.genKeyPair(jsch, KeyPair.ED25519);
//            ed25519 keys only exist in the OpenSSH format
            pair.writeOpenSSHv1PrivateKey(key.getAbsolutePath());
            pair.writePublicKey(key.getAbsolutePath() + ".pub", "t-ui@" + android.os.Build.MODEL.replace(' ', '-'));
            pair.dispose();
        } catch (Exception e) {
            key.delete();
            return "Key generation failed: " + e;
        }

        return "New ed25519 key created. Add this line to ~/.ssh/authorized_keys on your server:" + Tuils.NEWLINE + publicKey();
    }

    public static String publicKey() {
        File pub = new File(defaultKey().getAbsolutePath() + ".pub");
        if (!pub.exists()) return "No key yet. Create one with: ssh -keygen";

        try {
            try (InputStream in = new java.io.FileInputStream(pub)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
                return bytes.toString("UTF-8").trim();
            }
        } catch (Exception e) {
            return e.toString();
        }
    }

    public static String forgetHost(String host) {
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(knownHosts().getAbsolutePath());
            HostKeyRepository repo = jsch.getHostKeyRepository();
//            hosts on another port than 22 are saved as [host]:port
            int removed = 0;
            HostKey[] keys = repo.getHostKey();
            if (keys != null) {
                for (HostKey k : keys) {
                    for (String h : k.getHost().split(",")) {
                        if (h.equals(host) || h.startsWith("[" + host + "]:")) {
                            repo.remove(h, k.getType());
                            removed++;
                        }
                    }
                }
            }
            return removed > 0 ? "Removed the saved key of " + host : "No saved key for " + host;
        } catch (Exception e) {
            return e.toString();
        }
    }

    // ---------------------------------------------------------------- session

    public static class Target {
        public String user, host, keyPath, remoteCommand;
        public int port = 22;
    }

    public static String start(Context context, Target target) {
        if (current != null) return "An ssh session is already open. Type " + DISCONNECT + " to close it";

        SshManager manager = new SshManager(context.getApplicationContext(), target);
        current = manager;
        manager.publishState();
        new Thread(manager::run, "t-ui-ssh").start();
        return null;
    }

    private final Context context;
    private final Target target;

    private final BlockingQueue<String> answers = new LinkedBlockingQueue<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
//    lines sent to the remote shell, used to drop the echo printed back by readline
    private final LinkedList<String> pendingEcho = new LinkedList<>();

    private volatile boolean waitingAnswer = false;
    private volatile boolean secretExpected = false;
    private volatile boolean closedByUser = false;
    private volatile String remotePrompt = null;

    private volatile Session session;
    private volatile OutputStream remoteIn;

    private SshManager(Context context, Target target) {
        this.context = context;
        this.target = target;
    }

    private void run() {
        String label = target.user + "@" + target.host;
        output("Connecting to " + label + (target.port != 22 ? ":" + target.port : Tuils.EMPTYSTRING) + "...  (" + DISCONNECT + " to cancel)");

        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(knownHosts().getAbsolutePath());

            File key = target.keyPath != null ? new File(target.keyPath) : defaultKey();
            if (key.exists()) jsch.addIdentity(key.getAbsolutePath());
            else if (target.keyPath != null) output("Key not found: " + target.keyPath);

            session = jsch.getSession(target.user, target.host, target.port);
            session.setUserInfo(new Prompter());
            session.setConfig("StrictHostKeyChecking", "ask");
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            session.setServerAliveInterval(KEEPALIVE_INTERVAL);
            session.connect(CONNECT_TIMEOUT);

            if (target.remoteCommand != null) runCommand();
            else runShell();
        } catch (Exception e) {
            if (!closedByUser) error(describe(e));
        } finally {
            close();
        }
    }

    private void runCommand() throws Exception {
        ChannelExec exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(target.remoteCommand);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        exec.setErrStream(err);
        InputStream in = exec.getInputStream();
        exec.connect(CONNECT_TIMEOUT);

        pump(in);

        while (!exec.isClosed()) Thread.sleep(50);
        String errText = err.toString("UTF-8").trim();
        if (errText.length() > 0) output(clean(errText));
        if (exec.getExitStatus() != 0) output("[exit " + exec.getExitStatus() + "]");
        exec.disconnect();
    }

    private void runShell() throws Exception {
        ChannelShell shell = (ChannelShell) session.openChannel("shell");
//        "dumb" asks programs not to draw with escape codes. ECHO off (opcode 53) avoids seeing every command twice
        shell.setPtyType("dumb", 80, 24, 0, 0);
        shell.setTerminalMode(new byte[] {53, 0, 0, 0, 0, 0});
        InputStream in = shell.getInputStream();
        remoteIn = shell.getOutputStream();
        shell.connect(CONNECT_TIMEOUT);

        output("Connected. Type " + DISCONNECT + " to disconnect, " + CTRL_C + " for Ctrl+C, " + CTRL_D + " for Ctrl+D");
        publishState();

        pump(in);
        shell.disconnect();
    }

//    reads the remote output until the channel closes, line by line
    private void pump(InputStream in) throws Exception {
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        StringBuilder pending = new StringBuilder();
        char[] buf = new char[4096];
        int n;

        while ((n = reader.read(buf)) != -1) {
            pending.append(buf, 0, n);

            int nl;
            while ((nl = pending.indexOf("\n")) != -1) {
                String line = clean(pending.substring(0, nl));
                pending.delete(0, nl + 1);
                emitLine(line);
            }

            if (pending.length() > 0) {
                Thread.sleep(PARTIAL_LINE_DELAY);
                if (!reader.ready()) {
                    emitPartial(clean(pending.toString()));
                    pending.setLength(0);
                }
            }
        }

        if (pending.length() > 0) emitLine(clean(pending.toString()));
    }

    private void emitLine(String line) {
        synchronized (pendingEcho) {
            if (!pendingEcho.isEmpty()) {
                String expected = pendingEcho.peekFirst();
                if (line.equals(expected) || (expected.length() > 0 && line.endsWith(expected))) {
                    pendingEcho.removeFirst();
                    return;
                }
            }
        }
        output(line);
    }

//    a line that doesn't end with '\n': a shell prompt becomes the t-ui prefix, anything else is printed
    private void emitPartial(String text) {
        if (text.trim().length() == 0) return;

        boolean secret = SECRET_PROMPT.matcher(text).matches();
        if (!secret && SHELL_PROMPT.matcher(text).matches() && text.length() < 80) {
            remotePrompt = text.trim();
            synchronized (pendingEcho) {
                pendingEcho.clear();
            }
        } else {
            output(text);
        }

        secretExpected = secret;
        publishState();
    }

    private void handleInput(String line) {
        String trimmed = line.trim();

        if (trimmed.equals(DISCONNECT)) {
            closedByUser = true;
            output("Disconnected");
            answers.offer(DISCONNECT);
            new Thread(this::close).start();
            return;
        }

        if (waitingAnswer) {
            answers.offer(line);
            return;
        }

        final OutputStream out = remoteIn;
        if (out == null) {
            if (trimmed.length() == 0) return;
            output("Still connecting... (" + DISCONNECT + " to cancel)");
            return;
        }

        final String toSend;
        if (trimmed.equals(CTRL_C)) toSend = "\u0003";
        else if (trimmed.equals(CTRL_D)) toSend = "\u0004";
        else {
            toSend = line + "\n";
            if (!secretExpected) {
                synchronized (pendingEcho) {
                    pendingEcho.addLast(line.trim());
                    while (pendingEcho.size() > 5) pendingEcho.removeFirst();
                }
            }
        }

        if (secretExpected) {
            secretExpected = false;
            publishState();
        }

//        network can't be touched from the UI thread
        writer.execute(() -> {
            try {
                out.write(toSend.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (Exception e) {
                error(describe(e));
            }
        });
    }

//    blocks the ssh thread until the user types an answer in t-ui
    private String ask(String question, boolean secret) {
        output(question);
        waitingAnswer = true;
        secretExpected = secret;
        publishState();

        try {
            String answer = answers.poll(ANSWER_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (answer == null || answer.trim().equals(DISCONNECT)) return null;
            return answer;
        } catch (InterruptedException e) {
            return null;
        } finally {
            waitingAnswer = false;
            secretExpected = false;
            publishState();
        }
    }

    private synchronized void close() {
        if (current != this) return;
        current = null;

        try {
            if (session != null) session.disconnect();
        } catch (Exception e) {
            Tuils.log(e);
        }
        writer.shutdown();

        if (!closedByUser) output("Connection to " + target.host + " closed");
        publishState();
    }

    private void publishState() {
        Intent i = new Intent(UIManager.ACTION_SSH_STATE);
        if (current == this) {
            i.putExtra(UIManager.SSH_PREFIX, remotePrompt != null ? remotePrompt : target.user + "@" + target.host + " $");
            i.putExtra(UIManager.SSH_SECRET, secretExpected);
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    private void output(String text) {
        Tuils.sendOutput(context, text, TerminalManager.CATEGORY_OUTPUT);
    }

    private void error(String text) {
        Tuils.sendOutput(Color.RED, context, text);
    }

    static String clean(String s) {
        s = ANSI.matcher(s).replaceAll(Tuils.EMPTYSTRING);

        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\b') {
                if (b.length() > 0) b.setLength(b.length() - 1);
            } else if (c == '\t' || c >= ' ') {
                b.append(c);
            }
//            other control chars (\r, bell...) are dropped
        }
        return b.toString();
    }

    private String describe(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.toString();

        if (msg.contains("Auth fail") || msg.contains("Auth cancel")) return "Authentication failed";
        if (msg.contains("UnknownHostException")) return "Unknown host";
        if (msg.contains("timeout") || msg.contains("timed out")) return "Connection timed out";
        if (msg.contains("Connection refused")) return "Connection refused (is sshd running? right port?)";
        if (msg.contains("reject HostKey") || msg.contains("HostKey has been changed")) {
            return "Host key rejected. If the server was reinstalled, type: ssh -forget " + target.host;
        }
        return msg;
    }

    private class Prompter implements UserInfo, UIKeyboardInteractive {

        private String password, passphrase;

        @Override
        public String getPassphrase() {
            return passphrase;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public boolean promptPassword(String message) {
            password = ask(message + ":", true);
            return password != null;
        }

        @Override
        public boolean promptPassphrase(String message) {
            passphrase = ask(message + ":", true);
            return passphrase != null;
        }

        @Override
        public boolean promptYesNo(String message) {
            String answer = ask(message + " (yes/no)", false);
            return answer != null && (answer.trim().equalsIgnoreCase("yes") || answer.trim().equalsIgnoreCase("y")
                    || answer.trim().equalsIgnoreCase("oui") || answer.trim().equalsIgnoreCase("o"));
        }

        @Override
        public void showMessage(String message) {
            output(message);
        }

        @Override
        public String[] promptKeyboardInteractive(String destination, String name, String instruction, String[] prompt, boolean[] echo) {
            if (instruction != null && instruction.length() > 0) output(instruction);

            List<String> result = new ArrayList<>();
            for (int i = 0; i < prompt.length; i++) {
                String answer = ask(prompt[i].trim(), !echo[i]);
                if (answer == null) return null;
                result.add(answer);
            }
            return result.toArray(new String[0]);
        }
    }
}
