package ohi.andre.consolelauncher.commands.main.raw;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

/**
 * notif on|off|list|clear: notifications inside the terminal
 */

public class notif implements CommandAbstraction {

    @Override
    public String exec(ExecutePack pack) throws Exception {
        String arg = pack.getString().trim().toLowerCase();

        switch (arg) {
            case "on":
            case "off":
                boolean on = arg.equals("on");
                Notifications.show_notifications.parent().write(Notifications.show_notifications, String.valueOf(on));
                if (on && !Tuils.hasNotificationAccess(pack.context)) {
                    Tuils.openNotificationAccessSettings(pack.context);
                    return "Allow T-UI in the list (notification access), then come back: new notifications will be shown here";
                }
                ((Reloadable) pack.context).reload();
                return null;
            case "list":
                return list(pack);
            case "clear":
                NotificationService service = NotificationService.connected;
                if (service == null) return notConnected(pack);
                service.cancelAllNotifications();
                return "Notifications cleared (the ones that can be dismissed)";
            default:
                return status(pack);
        }
    }

    private static String list(ExecutePack pack) {
        NotificationService service = NotificationService.connected;
        if (service == null) return notConnected(pack);

        StatusBarNotification[] active;
        try {
            active = service.getActiveNotifications();
        } catch (Exception e) {
            return notConnected(pack);
        }
        if (active == null || active.length == 0) return "No notification";

        PackageManager pm = pack.context.getPackageManager();
        StringBuilder b = new StringBuilder();
        for (StatusBarNotification sbn : active) {
            Bundle extras = sbn.getNotification().extras;
            CharSequence title = extras != null ? extras.getCharSequence(Notification.EXTRA_TITLE) : null;
            CharSequence text = extras != null ? extras.getCharSequence(Notification.EXTRA_TEXT) : null;
            if (title == null && text == null) continue;
//            ongoing ones (music, downloads, t-ui itself) are noise here
            if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) continue;

            String app = sbn.getPackageName();
            try {
                ApplicationInfo info = pm.getApplicationInfo(app, 0);
                app = pm.getApplicationLabel(info).toString();
            } catch (Exception ignored) {}

            if (b.length() > 0) b.append(Tuils.NEWLINE);
            b.append('[').append(app).append("] ");
            if (title != null) b.append(title);
            if (title != null && text != null) b.append(" - ");
            if (text != null) b.append(text);
        }
        return b.length() == 0 ? "No notification" : b.toString();
    }

    private static String notConnected(ExecutePack pack) {
        if (!Tuils.hasNotificationAccess(pack.context)) return "T-UI has no notification access. Type: notif on";
        return "The notification service isn't running. Type: notif on";
    }

    private static String status(ExecutePack pack) {
        boolean on = XMLPrefsManager.getBoolean(Notifications.show_notifications);
        return "Notifications in the terminal: " + (on ? "on" : "off")
                + Tuils.NEWLINE + "Notification access: " + (Tuils.hasNotificationAccess(pack.context) ? "yes" : "no")
                + Tuils.NEWLINE + Tuils.NEWLINE + pack.context.getString(R.string.help_notif);
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
        return R.string.help_notif;
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
