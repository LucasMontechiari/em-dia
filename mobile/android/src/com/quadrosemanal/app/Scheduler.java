package com.quadrosemanal.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Agenda os lembretes no sistema para que cheguem com o app fechado. */
final class Scheduler {
    static final String CHANNEL = "lembretes";
    static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final String PREFS = "reminders";

    private Scheduler() { }

    static void createChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Lembretes de tarefas",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Avisos das suas tarefas");
        ch.enableVibration(true);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Recebe a lista vinda do JavaScript, cancela os alarmes antigos e agenda os novos. */
    static synchronized void sync(Context c, String json) {
        try {
            JSONArray arr = new JSONArray(json);
            cancelAll(c);
            prefs(c).edit().putString("list", arr.toString()).apply();
            scheduleAll(c, arr);
        } catch (Exception ignored) { }
    }

    /** Depois de reiniciar o celular, os alarmes somem; recria a partir da última lista salva. */
    static synchronized void restore(Context c) {
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("list", "[]"));
            scheduleAll(c, arr);
        } catch (Exception ignored) { }
    }

    private static void scheduleAll(Context c, JSONArray arr) throws Exception {
        long now = System.currentTimeMillis();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            long at = o.getLong("at");
            boolean repeat = o.optBoolean("repeat");
            if (at <= now) {
                if (!repeat) continue;
                while (at <= now) at += WEEK;
            }
            int id = o.getInt("id");
            schedule(c, id, at, o.optString("title"), o.optString("body"), repeat);
            if (ids.length() > 0) ids.append(',');
            ids.append(id);
        }
        prefs(c).edit().putString("ids", ids.toString()).apply();
    }

    static void schedule(Context c, int id, long at, String title, String body, boolean repeat) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .putExtra("id", id).putExtra("at", at)
                .putExtra("title", title).putExtra("body", body).putExtra("repeat", repeat);
        PendingIntent pi = PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    private static void cancelAll(Context c) {
        String ids = prefs(c).getString("ids", "");
        if (ids.isEmpty()) return;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        for (String s : ids.split(",")) {
            try {
                int id = Integer.parseInt(s);
                PendingIntent pi = PendingIntent.getBroadcast(c, id, new Intent(c, AlarmReceiver.class),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) { am.cancel(pi); pi.cancel(); }
            } catch (NumberFormatException ignored) { }
        }
    }

    static void show(Context c, int id, String title, String body) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF0F7B6C)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(content)
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
