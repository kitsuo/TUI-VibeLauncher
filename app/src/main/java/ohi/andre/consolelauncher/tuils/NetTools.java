package ohi.andre.consolelauncher.tuils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local network helpers for scan, ports and myip.
 */

public class NetTools {

    public static class LocalAddress {
        public final String iface;
        public final Inet4Address address;
        public final int prefix;

        LocalAddress(String iface, Inet4Address address, int prefix) {
            this.iface = iface;
            this.address = address;
            this.prefix = prefix;
        }
    }

    public static List<LocalAddress> localIPv4() {
        List<LocalAddress> list = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address) {
                        list.add(new LocalAddress(ni.getName(), (Inet4Address) ia.getAddress(), ia.getNetworkPrefixLength()));
                    }
                }
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
        return list;
    }

//    wifi first, then ethernet / usb, then anything else (mobile data)
    public static LocalAddress lanAddress() {
        LocalAddress best = null;
        int bestScore = -1;
        for (LocalAddress a : localIPv4()) {
            int score = a.iface.startsWith("wlan") ? 3 : a.iface.startsWith("eth") || a.iface.startsWith("rndis") ? 2 : a.address.isSiteLocalAddress() ? 1 : 0;
            if (score > bestScore) {
                best = a;
                bestScore = score;
            }
        }
        return best;
    }

    public static int toInt(InetAddress a) {
        byte[] b = a.getAddress();
        return ((b[0] & 0xff) << 24) | ((b[1] & 0xff) << 16) | ((b[2] & 0xff) << 8) | (b[3] & 0xff);
    }

    public static String toString(int ip) {
        return ((ip >>> 24) & 0xff) + "." + ((ip >>> 16) & 0xff) + "." + ((ip >>> 8) & 0xff) + "." + (ip & 0xff);
    }
}
