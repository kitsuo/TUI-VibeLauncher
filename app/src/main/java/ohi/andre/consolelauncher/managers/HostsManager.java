package ohi.andre.consolelauncher.managers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Saved servers ("ssh srv" instead of "ssh root@192.168.10.100 -p 22").
 * Stored in ssh/hosts.txt, one per line: name user@host:port [key=path] [mac=AA:BB:CC:DD:EE:FF]
 */

public class HostsManager {

    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    public static final Pattern MAC = Pattern.compile("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}");

    public static class Host {
        public String name, user, address, key, mac;
        public int port = 22;

        public String destination() {
            return user + "@" + address + (port != 22 ? ":" + port : "");
        }

        String serialize() {
            StringBuilder b = new StringBuilder(name).append(' ').append(user).append('@').append(address).append(':').append(port);
            if (key != null) b.append(" key=").append(key);
            if (mac != null) b.append(" mac=").append(mac);
            return b.toString();
        }

        static Host parse(String line) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 2 || line.trim().startsWith("#")) return null;

            Host h = new Host();
            h.name = parts[0];
            if (!h.setDestination(parts[1])) return null;

            for (int i = 2; i < parts.length; i++) {
                if (parts[i].startsWith("key=")) h.key = parts[i].substring(4);
                else if (parts[i].startsWith("mac=")) h.mac = parts[i].substring(4);
            }
            return h;
        }

//        user@host[:port]
        public boolean setDestination(String destination) {
            int at = destination.lastIndexOf('@');
            if (at <= 0 || at == destination.length() - 1) return false;

            user = destination.substring(0, at);
            address = destination.substring(at + 1);

            int colon = address.lastIndexOf(':');
            if (colon > 0 && address.indexOf(':') == colon) {
                try {
                    port = Integer.parseInt(address.substring(colon + 1));
                    address = address.substring(0, colon);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
    }

    private static File file() {
        return new File(SshManager.sshFolder(), "hosts.txt");
    }

    public static synchronized List<Host> all() {
        List<Host> hosts = new ArrayList<>();
        File f = file();
        if (!f.exists()) return hosts;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Host h = Host.parse(line);
                if (h != null) hosts.add(h);
            }
        } catch (Exception e) {
            ohi.andre.consolelauncher.tuils.Tuils.log(e);
        }
        return hosts;
    }

    public static Host get(String name) {
        if (name == null) return null;
        for (Host h : all()) {
            if (h.name.equalsIgnoreCase(name)) return h;
        }
        return null;
    }

    public static List<String> names() {
        List<String> names = new ArrayList<>();
        for (Host h : all()) names.add(h.name);
        return names;
    }

//    replaces a host with the same name
    public static synchronized void save(Host host) throws Exception {
        List<Host> hosts = all();
        boolean replaced = false;
        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).name.equalsIgnoreCase(host.name)) {
                hosts.set(i, host);
                replaced = true;
            }
        }
        if (!replaced) hosts.add(host);
        write(hosts);
    }

    public static synchronized boolean remove(String name) throws Exception {
        List<Host> hosts = all();
        boolean removed = false;
        for (int i = hosts.size() - 1; i >= 0; i--) {
            if (hosts.get(i).name.equalsIgnoreCase(name)) {
                hosts.remove(i);
                removed = true;
            }
        }
        if (removed) write(hosts);
        return removed;
    }

    private static void write(List<Host> hosts) throws Exception {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file()), StandardCharsets.UTF_8)) {
            for (Host h : hosts) w.write(h.serialize() + "\n");
        }
    }

    public static String normalizeMac(String mac) {
        return mac.replace('-', ':').toUpperCase(Locale.ROOT);
    }
}
