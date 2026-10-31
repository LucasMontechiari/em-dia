package com.quadrosemanal.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dispara quando chega a hora do lembrete. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Scheduler.createChannel(c);
        int id = i.getIntExtra("id", 0);
        Scheduler.show(c, id, i.getStringExtra("title"), i.getStringExtra("body"));
        if (i.getBooleanExtra("repeat", false)) {
            long next = i.getLongExtra("at", System.currentTimeMillis()) + Scheduler.WEEK;
            Scheduler.schedule(c, id, next, i.getStringExtra("title"), i.getStringExtra("body"), true);
        }
    }
}
