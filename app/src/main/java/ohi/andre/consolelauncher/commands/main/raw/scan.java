package ohi.andre.consolelauncher.commands.main.raw;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.HostsManager;
import ohi.andre.consolelauncher.managers.ServersStatus;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.tuils.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * scan: lists the devices that answer on the local network (wifi)
 */

public class scan implements CommandAbstraction {

    private static final int PING_TIMEOUT = 800;
    private static final int TCP_TIMEOUT = 300;
//    many devices don't answer ping but have one of these open
    private static final int[] FALLBACK_PORTS = {22, 80, 443, 445};

    @Override
    public String exec(ExecutePack pack) throws Exception {
        NetTools.LocalAddress local = NetTools.lanAddress();
        if (local == null) return "Not connected to a network";

//        at most a /24 (254 addresses), around our own address
        int prefix = Math.max(local.prefix, 24);
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        int me = NetTools.toInt(local.address);
        int network = me & mask;
        int count = (1 << (32 - prefix)) - 2;

        Tuils.sendOutput(pack.context, "Scanning " + NetTools.toString(network) + "/" + prefix + " (" + local.iface + ")...", TerminalManager.CATEGORY_OUTPUT);

        List<HostsManager.Host> saved = HostsManager.all();
        ExecutorService pool = Executors.newFixedThreadPool(128);
        List<Future<String>> results = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int i = 1; i <= count; i++) {
            final int ip = network + i;
            results.add(pool.submit(() -> probe(ip, ip == me, saved)));
        }

        StringBuilder b = new StringBuilder();
        int found = 0;
        for (Future<String> f : results) {
            String line;
            try {
                line = f.get();
            } catch (Exception e) {
                line = null;
            }
            if (line != null) {
                if (b.length() > 0) b.append(Tuils.NEWLINE);
                b.append(line);
                found++;
            }
        }
        pool.shutdownNow();

        b.append(Tuils.NEWLINE).append(found).append(" device(s) found in ").append((System.currentTimeMillis() - start) / 1000).append("s");
        return b.toString();
    }

    private static String probe(int ip, boolean self, List<HostsManager.Host> saved) {
        String address = NetTools.toString(ip);
        String how = null;

        if (self) how = "this phone";
        else {
            try {
                long t = System.nanoTime();
                if (InetAddress.getByName(address).isReachable(PING_TIMEOUT)) how = Math.max(1, (System.nanoTime() - t) / 1000000) + "ms";
            } catch (Exception ignored) {}

            if (how == null) {
                for (int port : FALLBACK_PORTS) {
                    if (ServersStatus.latency(address, port, TCP_TIMEOUT) >= 0) {
                        how = "port " + port;
                        break;
                    }
                }
            }
        }

        if (how == null) return null;

        StringBuilder line = new StringBuilder(address);
        while (line.length() < 16) line.append(' ');
        line.append(how);
        for (HostsManager.Host h : saved) {
            if (h.address.equals(address)) line.append("  [").append(h.name).append(']');
        }
        return line.toString();
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
        return R.string.help_scan;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_scan);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_scan);
    }
}
