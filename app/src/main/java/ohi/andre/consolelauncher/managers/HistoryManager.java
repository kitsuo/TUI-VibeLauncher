package ohi.andre.consolelauncher.managers;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ohi.andre.consolelauncher.tuils.ProcessInterrupter;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Persistent command history, stored one command per line in the t-ui folder.
 */

public class HistoryManager {

    public static HistoryManager instance;

    private static final String FILE_NAME = "history.txt";
    private static final int MAX_SIZE = 500;

    private final File file;
//    oldest first
    private final LinkedList<String> entries = new LinkedList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public HistoryManager(Context context) {
        Tuils.init(context);
        file = new File(Tuils.getFolder(), FILE_NAME);
        load();

        instance = this;
    }

    private synchronized void load() {
        entries.clear();
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) entries.add(line);
            }
        } catch (Exception e) {
            Tuils.log(e);
        }

        while (entries.size() > MAX_SIZE) entries.removeFirst();
    }

    public synchronized void add(String cmd) {
        if (cmd == null) return;

        cmd = cmd.replace('\n', ' ').replace('\r', ' ').trim();
//        don't pollute the history with the commands used to browse it
        if (cmd.length() == 0 || cmd.equals("history") || cmd.startsWith("history ") || ProcessInterrupter.isCtrlC(cmd)) return;

//        keep only the latest occurrence of a command
        entries.remove(cmd);
        entries.addLast(cmd);
        while (entries.size() > MAX_SIZE) entries.removeFirst();

        save();
    }

//    most recent first
    public synchronized List<String> recent(int max) {
        List<String> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && result.size() < max; i--) {
            result.add(entries.get(i));
        }
        return result;
    }

//    most recent first, case-insensitive
    public synchronized List<String> search(String query, int max) {
        if (query == null || query.trim().length() == 0) return recent(max);

        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && result.size() < max; i--) {
            String entry = entries.get(i);
            if (entry.toLowerCase(Locale.ROOT).contains(q)) result.add(entry);
        }
        return result;
    }

    public synchronized void clear() {
        entries.clear();
        save();
    }

    private void save() {
        final List<String> snapshot = new ArrayList<>(entries);
        writer.execute(() -> {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                for (String s : snapshot) {
                    w.write(s);
                    w.write('\n');
                }
            } catch (Exception e) {
                Tuils.log(e);
            }
        });
    }
}
