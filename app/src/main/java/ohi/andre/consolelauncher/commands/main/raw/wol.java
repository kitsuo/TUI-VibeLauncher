package ohi.andre.consolelauncher.commands.main.raw;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.HostsManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Wake-on-LAN: wol [saved server | mac address]
 */

public class wol implements CommandAbstraction {

    private static final int PORT = 9;

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String arg = pack.getString().trim();

        String mac, name;
        if (HostsManager.MAC.matcher(arg).matches()) {
            mac = HostsManager.normalizeMac(arg);
            name = mac;
        } else {
            HostsManager.Host h = HostsManager.get(arg);
            if (h == null) return "Unknown server " + arg + Tuils.NEWLINE + list();
            if (h.mac == null) return h.name + " has no MAC address. Add it: ssh -add " + h.name + " " + h.destination() + " -mac AA:BB:CC:DD:EE:FF";
            mac = h.mac;
            name = h.name;
        }

        byte[] packet = magicPacket(mac);
        List<InetAddress> targets = broadcastAddresses();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
//            udp can be lost: send it a few times
            for (int i = 0; i < 3; i++) {
                for (InetAddress a : targets) socket.send(new DatagramPacket(packet, packet.length, a, PORT));
            }
        }

        return "Magic packet sent to " + name + (name.equals(mac) ? "" : " (" + mac + ")") + ". It can take a minute to boot.";
    }

    static byte[] magicPacket(String mac) {
        String[] hex = mac.split("[:-]");
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) bytes[i] = (byte) Integer.parseInt(hex[i], 16);

        byte[] packet = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) packet[i] = (byte) 0xff;
        for (int i = 6; i < packet.length; i += 6) System.arraycopy(bytes, 0, packet, i, 6);
        return packet;
    }

//    the broadcast of every network we're on (wifi, usb...), plus 255.255.255.255
    static List<InetAddress> broadcastAddresses() throws Exception {
        List<InetAddress> list = new ArrayList<>();
        list.add(InetAddress.getByName("255.255.255.255"));
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                if (ia.getBroadcast() != null) list.add(ia.getBroadcast());
            }
        }
        return list;
    }

    private static String list() {
        StringBuilder b = new StringBuilder();
        for (HostsManager.Host h : HostsManager.all()) {
            if (h.mac != null) b.append(Tuils.NEWLINE).append(h.name).append(" -> ").append(h.mac);
        }
        return b.length() == 0 ? "No saved server has a MAC address. Add one: ssh -add nas root@192.168.10.20 -mac AA:BB:CC:DD:EE:FF"
                : "Servers with a MAC address:" + b;
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
        return R.string.help_wol;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_wol);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return list() + Tuils.NEWLINE + Tuils.NEWLINE + pack.context.getString(R.string.help_wol);
    }
}
