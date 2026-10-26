package ohi.andre.consolelauncher.commands.main.raw;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.HistoryManager;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * Show and search the persistent command history.
 */

public class history implements CommandAbstraction {

    private static final int MAX_RESULTS = 30;
    private static final String CLEAR = "-clear";

    @Override
    public String exec(ExecutePack pack) throws Exception {
        HistoryManager manager = HistoryManager.instance;
        if (manager == null) return pack.context.getString(R.string.output_error);

        String query = pack.getString().trim();
        if (query.equalsIgnoreCase(CLEAR)) {
            manager.clear();
            return pack.context.getString(R.string.output_history_cleared);
        }

        List<String> result = manager.search(query, MAX_RESULTS);
        if (result.isEmpty()) return pack.context.getString(R.string.output_history_no_match);
        return format(result);
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
        return R.string.help_history;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_history);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        HistoryManager manager = HistoryManager.instance;
        if (manager == null) return pack.context.getString(R.string.output_error);

        List<String> result = manager.recent(MAX_RESULTS);
        if (result.isEmpty()) return pack.context.getString(R.string.output_history_empty);
        return format(result);
    }

//    oldest at the top, so the latest command ends up right above the input, like in a shell
    private static String format(List<String> mostRecentFirst) {
        StringBuilder builder = new StringBuilder();
        for (int i = mostRecentFirst.size() - 1; i >= 0; i--) {
            builder.append(mostRecentFirst.get(i));
            if (i > 0) builder.append(Tuils.NEWLINE);
        }
        return builder.toString();
    }
}
