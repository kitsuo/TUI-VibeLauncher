package ohi.andre.consolelauncher.commands.main.raw;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/**
 * Visual effects: fx crt on|off, fx typing on|off, fx speed [ms per character]
 */

public class fx implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String[] args = pack.getString().trim().toLowerCase().split("\\s+");
        if (args.length < 2) return status(pack);

        XMLPrefsSave save;
        String value;
        switch (args[0]) {
            case "crt":
                save = Ui.crt_effect;
                value = onOff(args[1]);
                break;
            case "typing":
                save = Ui.typing_animation;
                value = onOff(args[1]);
                break;
            case "boot":
                save = Ui.boot_animation;
                value = onOff(args[1]);
                break;
            case "speed":
                save = Ui.typing_speed;
                try {
                    int ms = Integer.parseInt(args[1]);
                    value = ms >= 1 && ms <= 200 ? String.valueOf(ms) : null;
                } catch (NumberFormatException e) {
                    value = null;
                }
                if (value == null) return "Speed: milliseconds per character, between 1 (fast) and 200 (slow)";
                break;
            default:
                return pack.context.getString(R.string.help_fx);
        }

        if (value == null) return "Use on or off";

        save.parent().write(save, value);
        ((Reloadable) pack.context).reload();
        return null;
    }

    private static String onOff(String s) {
        switch (s) {
            case "on": case "true": case "1": case "yes":
                return "true";
            case "off": case "false": case "0": case "no":
                return "false";
        }
        return null;
    }

    private static String status(ExecutePack pack) {
        return "crt: " + (XMLPrefsManager.getBoolean(Ui.crt_effect) ? "on" : "off")
                + "\ntyping: " + (XMLPrefsManager.getBoolean(Ui.typing_animation) ? "on" : "off")
                + " (" + XMLPrefsManager.getInt(Ui.typing_speed) + " ms per character)"
                + "\nboot: " + (XMLPrefsManager.getBoolean(Ui.boot_animation) ? "on" : "off")
                + "\n\n" + pack.context.getString(R.string.help_fx);
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
        return R.string.help_fx;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return status(pack);
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        return status(pack);
    }
}
