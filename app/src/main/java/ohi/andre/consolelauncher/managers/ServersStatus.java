package ohi.andre.consolelauncher.managers;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Checks if the saved servers answer (tcp connection to their ssh port), for the status line.
 */

public class ServersStatus {

    public static final int TIMEOUT_MS = 2000;

    public static class Result {
        public final HostsManager.Host host;
        public final long latency;   // -1 = down

        Result(HostsManager.Host host, long latency) {
            this.host = host;
            this.latency = latency;
        }
    }

//    blocking: call it from a background thread
    public static List<Result> check(List<HostsManager.Host> hosts) {
        List<Result> results = new ArrayList<>();
        if (hosts.isEmpty()) return results;

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(hosts.size(), 8));
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (HostsManager.Host h : hosts) futures.add(pool.submit(() -> latency(h.address, h.port, TIMEOUT_MS)));

            for (int i = 0; i < hosts.size(); i++) {
                long l;
                try {
                    l = futures.get(i).get(TIMEOUT_MS * 2, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    l = -1;
                }
                results.add(new Result(hosts.get(i), l));
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

//    milliseconds to open a tcp connection, -1 if it fails
    public static long latency(String host, int port, int timeout) {
        long start = System.nanoTime();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeout);
            return Math.max(1, (System.nanoTime() - start) / 1000000);
        } catch (Exception e) {
            return -1;
        }
    }

//    "● srv 12ms  ○ nas"
    public static CharSequence format(List<Result> results, int upColor, int downColor) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        for (Result r : results) {
            if (b.length() > 0) b.append("  ");
            int start = b.length();
            if (r.latency >= 0) b.append("● ").append(r.host.name).append(' ').append(String.valueOf(r.latency)).append("ms");
            else b.append("○ ").append(r.host.name).append(" down");
            b.setSpan(new ForegroundColorSpan(r.latency >= 0 ? upColor : downColor), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }
}
