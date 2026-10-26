package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Activity;
import android.content.Intent;
import androidx.core.app.ActivityCompat;

import ohi.andre.consolelauncher.LauncherActivity;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.tuils.TermuxBridge;
import ohi.andre.consolelauncher.tuils.Tuils;

/**
 * termux [command]       run a command in Termux, output shown in t-ui
 * termux -open [command] run it in a Termux window (for interactive programs)
 * termux -setup          allow t-ui to use Termux
 */

public class termux implements CommandAbstraction {

    private static final String OPEN = "-open";
    private static final String SETUP = "-setup";

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String input = pack.getString().trim();

        if (input.equals(SETUP)) return setup(pack);

        if (input.equals(OPEN)) {
            Intent open = pack.context.getPackageManager().getLaunchIntentForPackage(TermuxBridge.PACKAGE);
            if (open == null) return TermuxBridge.status(pack.context);
            pack.context.startActivity(open);
            return null;
        }

        if (input.startsWith(OPEN + " ")) {
            return TermuxBridge.run(pack.context, input.substring(OPEN.length()).trim(), true);
        }

        return TermuxBridge.run(pack.context, input, TermuxBridge.isInteractive(input));
    }

    private String setup(ExecutePack pack) {
        if (!TermuxBridge.isInstalled(pack.context)) {
            pack.context.startActivity(Tuils.webPage("https://f-droid.org/packages/com.termux/"));
            return "Install Termux from F-Droid (not from the Play Store, that version is outdated), then type termux -setup again";
        }

        StringBuilder b = new StringBuilder();
        if (!TermuxBridge.hasPermission(pack.context) && pack.context instanceof Activity) {
            ((MainPack) pack).lastCommand = "termux";
            ActivityCompat.requestPermissions((Activity) pack.context, new String[] {TermuxBridge.PERMISSION}, LauncherActivity.COMMAND_REQUEST_PERMISSION);
            b.append("1. Allow t-ui to run commands in Termux (system dialog)").append(Tuils.NEWLINE);
        } else {
            b.append("1. Permission: OK").append(Tuils.NEWLINE);
        }

        b.append("2. Open Termux once and type this line (only needed once):").append(Tuils.NEWLINE)
                .append(TermuxBridge.SETUP_COMMAND).append(Tuils.NEWLINE)
                .append("3. Then in t-ui: termux pkg install -y git (or any package)").append(Tuils.NEWLINE)
                .append("Linux commands that Android doesn't have are sent to Termux automatically.");
        return b.toString();
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
        return R.string.help_termux;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_termux);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return TermuxBridge.status(pack.context) + Tuils.NEWLINE + Tuils.NEWLINE + pack.context.getString(R.string.help_termux);
    }
}
