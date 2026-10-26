package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;

/**
 * findout [text]: highlights [text] in the output. findout alone removes the highlights
 */

public class findout implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        search(pack, pack.getString().trim());
        return null;
    }

    private static void search(ExecutePack pack, String text) {
        Intent i = new Intent(UIManager.ACTION_FIND_OUTPUT);
        i.putExtra(UIManager.FIND_TEXT, text);
        LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(i);
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
        return R.string.help_findout;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return pack.context.getString(R.string.help_findout);
    }

//    no text: clear
    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        search(pack, null);
        return null;
    }
}
