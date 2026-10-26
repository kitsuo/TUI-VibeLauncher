package ohi.andre.consolelauncher.commands.main.raw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.HostsManager;
import ohi.andre.consolelauncher.managers.ServersStatus;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * ports [host | saved server] [ports]   e.g. ports srv, ports 192.168.10.1 1-1024, ports nas 22,80,443
 */

public class ports implements CommandAbstraction {

    private static final int TIMEOUT = 600;
    private static final int MAX_PORTS = 10000;

    private static final Map<Integer, String> COMMON = new HashMap<>();
    static {
        Object[] c = {21, "ftp", 22, "ssh", 23, "telnet", 25, "smtp", 53, "dns", 80, "http", 110, "pop3", 139, "netbios",
                143, "imap", 443, "https", 445, "smb", 548, "afp", 631, "ipp", 993, "imaps", 1883, "mqtt", 2049, "nfs",
                3000, "dev", 3306, "mysql", 3389, "rdp", 5000, "dev/nas", 5432, "postgres", 5900, "vnc", 6379, "redis",
                8006, "proxmox", 8080, "http-alt", 8123, "home assistant", 8443, "https-alt", 8888, "http-alt",
                9000, "portainer", 9090, "cockpit/prometheus", 9100, "printer", 32400, "plex", 51820, "wireguard"};
        for (int i = 0; i < c.length; i += 2) COMMON.put((Integer) c[i], (String) c[i + 1]);
    }

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String[] args = pack.getString().trim().split("\\s+");

        String host = args[0];
        HostsManager.Host saved = HostsManager.get(host);
        if (saved != null) host = saved.address;

        List<Integer> list;
        if (args.length > 1) {
            list = parse(args[1]);
            if (list == null) return "Write the ports like 22,80,443 or 1-1024 (max " + MAX_PORTS + ")";
        } else {
            list = new ArrayList<>(COMMON.keySet());
            java.util.Collections.sort(list);
        }

        Tuils.sendOutput(pack.context, "Checking " + list.size() + " port(s) on " + host + "...", TerminalManager.CATEGORY_OUTPUT);

        ExecutorService pool = Executors.newFixedThreadPool(64);
        List<Future<Long>> results = new ArrayList<>();
        final String target = host;
        for (int port : list) results.add(pool.submit(() -> ServersStatus.latency(target, port, TIMEOUT)));

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            long l;
            try {
                l = results.get(i).get();
            } catch (Exception e) {
                l = -1;
            }
            if (l < 0) continue;

            int port = list.get(i);
            String line = port + "/tcp";
            while (line.length() < 10) line += " ";
            line += "open";
            String name = COMMON.get(port);
            if (name != null) line += "  " + name;

            if (b.length() > 0) b.append(Tuils.NEWLINE);
            b.append(line);
        }
        pool.shutdownNow();

        return b.length() == 0 ? "No open port found (or " + host + " doesn't answer)" : b.toString();
    }

//    22,80,443 or 1-1024 or a mix
    private static List<Integer> parse(String s) {
        List<Integer> list = new ArrayList<>();
        try {
            for (String part : s.split(",")) {
                int dash = part.indexOf('-');
                int from = Integer.parseInt(dash == -1 ? part : part.substring(0, dash));
                int to = dash == -1 ? from : Integer.parseInt(part.substring(dash + 1));
                if (from < 1 || to > 65535 || from > to) return null;
                for (int p = from; p <= to; p++) {
                    list.add(p);
                    if (list.size() > MAX_PORTS) return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return list;
    }

    @Override
    public int[] argType() {
        return new int[] {CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_ports;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_ports);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_ports);
    }
}
