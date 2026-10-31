package com.quadrosemanal.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recria os lembretes depois que o celular reinicia ou o app é atualizado. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Scheduler.createChannel(c);
            Scheduler.restore(c);
        }
    }
}
