package ohi.andre.consolelauncher.commands.main.raw;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.tuils.NetTools;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * myip: local addresses, and the public one (asked to a "what is my ip" service)
 */

public class myip implements CommandAbstraction {

//    several services: some networks block one of them (pi-hole, adguard...)
    private static final String[] PUBLIC_IP_URLS = {"https://api.ipify.org", "https://icanhazip.com", "https://checkip.amazonaws.com", "https://ifconfig.me/ip"};
    private static final java.util.regex.Pattern IP = java.util.regex.Pattern.compile("[0-9a-fA-F.:]{3,45}");

    @Override
    public String exec(ExecutePack pack) throws Exception {
        StringBuilder b = new StringBuilder();
        for (NetTools.LocalAddress a : NetTools.localIPv4()) {
            if (b.length() > 0) b.append(Tuils.NEWLINE);
            String name = a.iface.startsWith("wlan") ? "wifi" : a.iface.startsWith("rmnet") || a.iface.startsWith("ccmni") ? "mobile" : a.iface;
            b.append(name).append(": ").append(a.address.getHostAddress()).append('/').append(a.prefix);
        }
        if (b.length() == 0) b.append("Not connected to a network");

        b.append(Tuils.NEWLINE).append("public: ").append(publicIp());
        return b.toString();
    }

    private static String publicIp() {
        for (String url : PUBLIC_IP_URLS) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String ip = r.readLine();
                    if (ip != null && IP.matcher(ip.trim()).matches()) return ip.trim();
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return "unknown (no internet, or these sites are blocked by your DNS)";
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
        return R.string.help_myip;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_myip);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return pack.context.getString(R.string.help_myip);
    }
}
