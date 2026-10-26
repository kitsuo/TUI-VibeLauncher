package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.HostsManager;
import ohi.andre.consolelauncher.managers.SshManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Built-in ssh client: ssh [-p port] [-i key] user@host [command]
 * Saved servers: ssh -add name user@host[:port] [-i key] [-mac mac], then ssh name
 */

public class ssh implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String[] args = pack.getString().trim().split("\\s+");

        switch (args[0]) {
            case "-keygen":
                return SshManager.generateKey(pack.context);
            case "-pubkey":
                return SshManager.publicKey();
            case "-forget":
                if (args.length < 2) return pack.context.getString(R.string.help_ssh);
                return SshManager.forgetHost(args[1]);
            case "-add":
                return add(pack, args);
            case "-rm":
                if (args.length < 2) return pack.context.getString(R.string.help_ssh);
                if (!HostsManager.remove(args[1])) return "No saved server named " + args[1];
                serversChanged(pack);
                return args[1] + " removed";
            case "-ls":
                return list();
        }

        SshManager.Target target = new SshManager.Target();
        int i = 0;
        for (; i < args.length; i++) {
            String a = args[i];
            if (a.equals("-p") && i + 1 < args.length) {
                try {
                    target.port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    return "Invalid port: " + args[i];
                }
            } else if (a.equals("-i") && i + 1 < args.length) {
                target.keyPath = keyPath(pack, args[++i]);
            } else if (a.startsWith("-")) {
                return "Unknown option: " + a + Tuils.NEWLINE + pack.context.getString(R.string.help_ssh);
            } else {
                break;
            }
        }

        if (i >= args.length) return pack.context.getString(R.string.help_ssh);

        String destination = args[i++];
        HostsManager.Host saved = destination.indexOf('@') == -1 ? HostsManager.get(destination) : null;

        if (saved != null) {
            target.user = saved.user;
            target.host = saved.address;
            if (target.port == 22) target.port = saved.port;
            if (target.keyPath == null) target.keyPath = saved.key;
        } else {
            HostsManager.Host parsed = new HostsManager.Host();
            if (!parsed.setDestination(destination)) {
                return "Unknown server " + destination + ". Write the user name too, e.g. ssh root@" + (destination.indexOf('@') == -1 ? destination : "192.168.1.10")
                        + Tuils.NEWLINE + "or save it: ssh -add " + (destination.indexOf('@') == -1 ? destination : "name") + " root@192.168.1.10";
            }
            target.user = parsed.user;
            target.host = parsed.address;
            if (parsed.port != 22) target.port = parsed.port;
        }

        if (i < args.length) {
            StringBuilder cmd = new StringBuilder();
            for (; i < args.length; i++) {
                if (cmd.length() > 0) cmd.append(' ');
                cmd.append(args[i]);
            }
            target.remoteCommand = cmd.toString();
        }

        return SshManager.start(pack.context, target);
    }

//    ssh -add name user@host[:port] [-p port] [-i key] [-mac AA:BB:CC:DD:EE:FF]
    private String add(ExecutePack pack, String[] args) throws Exception {
        if (args.length < 3) return "Usage: ssh -add [name] [user@host] (-p port) (-i key) (-mac address)";

        HostsManager.Host h = new HostsManager.Host();
        h.name = args[1];
        if (!HostsManager.NAME.matcher(h.name).matches()) return "Use only letters, digits, - _ . in the name";
        if (!h.setDestination(args[2])) return "Write it as user@host, e.g. root@192.168.10.100";

        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("-p") && i + 1 < args.length) {
                try {
                    h.port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    return "Invalid port: " + args[i];
                }
            } else if (args[i].equals("-i") && i + 1 < args.length) {
                h.key = keyPath(pack, args[++i]);
            } else if (args[i].equals("-mac") && i + 1 < args.length) {
                if (!HostsManager.MAC.matcher(args[++i]).matches()) return "Invalid MAC address: " + args[i];
                h.mac = HostsManager.normalizeMac(args[i]);
            } else {
                return "Unknown option: " + args[i];
            }
        }

        HostsManager.save(h);
        serversChanged(pack);
        return "Saved: " + h.name + " -> " + h.destination() + (h.mac != null ? " (mac " + h.mac + ")" : "")
                + Tuils.NEWLINE + "Connect with: ssh " + h.name;
    }

    private String list() {
        List<HostsManager.Host> hosts = HostsManager.all();
        if (hosts.isEmpty()) return "No saved server. Add one: ssh -add srv root@192.168.10.100";

        StringBuilder b = new StringBuilder();
        for (HostsManager.Host h : hosts) {
            if (b.length() > 0) b.append(Tuils.NEWLINE);
            b.append(h.name).append(" -> ").append(h.destination());
            if (h.key != null) b.append("  key ").append(h.key);
            if (h.mac != null) b.append("  mac ").append(h.mac);
        }
        return b.toString();
    }

    private static String keyPath(ExecutePack pack, String path) {
        File key = new File(path);
        if (!key.isAbsolute()) key = new File(((MainPack) pack).currentDirectory, path);
        return key.getAbsolutePath();
    }

//    the status line shows the saved servers
    private static void serversChanged(ExecutePack pack) {
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_SERVERS_CHANGED));
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
        return R.string.help_ssh;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_ssh);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_ssh);
    }
}
