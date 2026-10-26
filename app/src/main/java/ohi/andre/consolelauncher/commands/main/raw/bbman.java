package ohi.andre.consolelauncher.commands.main.raw;

import android.graphics.Color;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.tuils.BusyBoxInstaller;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.managers.TerminalManager;

public class bbman extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        install {
            @Override
            public String exec(ExecutePack pack) {
//                Android 10+ refuses to execute files downloaded into the app's data folder (W^X), so BusyBox can't run anymore
                return "BusyBox can't run on Android 10 and newer: apps are not allowed to execute downloaded programs."
                        + Tuils.NEWLINE + "Android's own commands (ls, cat, grep, ping, ps, ip...) work directly."
                        + Tuils.NEWLINE + "For other Linux tools, install Termux: termux -setup";
            }
        },
        remove {
            @Override
            public String exec(ExecutePack pack) {
                if (!BusyBoxInstaller.isInstalled(pack.context)) {
                    return "BusyBox is not installed.";
                }
                BusyBoxInstaller.uninstall(pack.context);
                return "BusyBox removed successfully.";
            }
        };

        static Param get(String p) {
            for (Param p1 : values()) if (p.endsWith(p1.label())) return p1;
            return null;
        }

        @Override
        public String label() { return "-" + name(); }
        @Override
        public int[] args() { return new int[0]; }
        @Override
        public String onNotArgEnough(ExecutePack pack, int n) { return null; }
        @Override
        public String onArgNotFound(ExecutePack pack, int index) { return null; }
    }

    @Override
    public String[] params() {
        Param[] ps = Param.values();
        String[] ss = new String[ps.length];
        for (int i = 0; i < ps.length; i++) ss[i] = ps[i].label();
        return ss;
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(ohi.andre.consolelauncher.commands.main.MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() { return 5; }

    @Override
    public int helpRes() { return R.string.help_bbman; }

    @Override
    protected String doThings(ExecutePack pack) { return null; }
}
