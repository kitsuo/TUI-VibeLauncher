package ohi.andre.consolelauncher.tuils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import ohi.andre.consolelauncher.managers.TerminalManager;

/**
 * Pipes and redirections for t-ui commands: scan | grep 192, fetch > phone.txt, history >> log.txt
 * (shell commands already have them, the shell does it)
 */

public class Pipes {

    public final String command;
    public final String pipe;       // shell command line reading the output, or null
    public final String file;       // redirection target, or null
    public final boolean append;

    private Pipes(String command, String pipe, String file, boolean append) {
        this.command = command;
        this.pipe = pipe;
        this.file = file;
        this.append = append;
    }

//    null when there's no unquoted | or > in the input
    public static Pipes split(String input) {
        char quote = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }

            if (c == '|') {
//                "||" is a shell "or", not a pipe
                if (i + 1 < input.length() && input.charAt(i + 1) == '|') return null;
                String command = input.substring(0, i).trim();
                String pipe = input.substring(i + 1).trim();
                if (command.length() == 0 || pipe.length() == 0) return null;
                return new Pipes(command, pipe, null, false);
            }

            if (c == '>') {
                boolean append = i + 1 < input.length() && input.charAt(i + 1) == '>';
                String command = input.substring(0, i).trim();
                String file = input.substring(i + (append ? 2 : 1)).trim();
                if (file.length() >= 2 && (file.charAt(0) == '"' || file.charAt(0) == '\'') && file.charAt(file.length() - 1) == file.charAt(0)) {
                    file = file.substring(1, file.length() - 1);
                }
                if (command.length() == 0 || file.length() == 0) return null;
                return new Pipes(command, null, file, append);
            }
        }
        return null;
    }

//    sends the captured output where it was asked to go
    public void deliver(Context context, File directory, String output) {
        if (output == null) output = Tuils.EMPTYSTRING;

        try {
            if (file != null) {
                File f = new File(file);
                if (!f.isAbsolute()) f = new File(directory, file);
                try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), StandardCharsets.UTF_8)) {
                    w.write(output);
                    if (!output.endsWith("\n")) w.write("\n");
                }
                Tuils.sendOutput(context, (append ? "Appended to " : "Written to ") + f.getAbsolutePath(), TerminalManager.CATEGORY_OUTPUT);
                return;
            }

//            the rest of the line goes to the shell, with our output as its input
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", pipe);
            if (directory != null && directory.isDirectory()) pb.directory(directory);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            try (OutputStream in = p.getOutputStream()) {
                in.write(output.getBytes(StandardCharsets.UTF_8));
                if (!output.endsWith("\n")) in.write('\n');
            } catch (Exception ignored) {
//                the command can stop reading early (head)
            }

            StringBuilder result = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (result.length() > 0) result.append(Tuils.NEWLINE);
                    result.append(line);
                }
            }
            p.waitFor();

            if (result.length() > 0) Tuils.sendOutput(context, result.toString(), TerminalManager.CATEGORY_OUTPUT);
        } catch (Exception e) {
            Tuils.sendOutput(context, e.getMessage() != null ? e.getMessage() : e.toString(), TerminalManager.CATEGORY_OUTPUT);
        }
    }
}
