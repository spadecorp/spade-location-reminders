package com.spadecorp.locationreminders;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.Task;
import org.json.*;
import java.util.*;

final class Fences {
    static final String CHANNEL = "arrivals_v1";
    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }
    static boolean granted(Context c, String name) {
        return c.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED;
    }
    static boolean canLocate(Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
            && (Build.VERSION.SDK_INT < 29 || granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION));
    }
    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Arrival reminders", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel("speech", "Reading a reminder", NotificationManager.IMPORTANCE_LOW));
    }
    static boolean canNotify(Context c) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT < 26) return true;
        NotificationChannel channel = c.getSystemService(NotificationManager.class).getNotificationChannel(CHANNEL);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    static List<JSONObject> items(Context c) {
        List<JSONObject> result = new ArrayList<>();
        for (Object value : prefs(c).getAll().values()) {
            try {result.add(new JSONObject((String)value));}
            catch (JSONException ignored) { /* Skip an unreadable legacy item. */ }
        }
        return result;
    }
    static Geofence fence(JSONObject row) throws JSONException {
        return new Geofence.Builder().setRequestId(row.getString("id"))
            .setCircularRegion(row.getDouble("lat"), row.getDouble("lng"), row.getInt("radius"))
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE).build();
    }
    @SuppressLint("MissingPermission")
    static Task<Void> register(Context c, List<Geofence> fences) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent target = PendingIntent.getBroadcast(c, 0, new Intent(c, EntryReceiver.class), flags);
        GeofencingRequest request = new GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build();
        return LocationServices.getGeofencingClient(c).addGeofences(request, target);
    }
    static String error(Exception e) {
        if (e instanceof com.google.android.gms.common.api.ApiException) {
            int status = ((com.google.android.gms.common.api.ApiException)e).getStatusCode();
            if (status == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) return "Enable Location and Google Location Accuracy, then reopen the app.";
            if (status == GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES) return "Device monitoring limit reached. Complete a reminder first.";
        }
        return "Could not register the alert. Check location permissions and Google Play services, then try again.";
    }
    static SharedPreferences health(Context c) {return c.getSharedPreferences("monitoring",Context.MODE_PRIVATE);}
    static void restore(Context c, Runnable done) {
        if (!canLocate(c)) {done.run();return;}
        try {
            List<Geofence> all = new ArrayList<>();
            for (JSONObject item : items(c)) all.add(fence(item));
            if (all.isEmpty()) {done.run();return;}
            register(c, all).addOnCompleteListener(task -> {
                health(c).edit().putString("error",task.isSuccessful() ? "" : error(task.getException())).apply();
                done.run();
            });
        } catch (Exception e) {health(c).edit().putString("error",error(e)).apply();done.run();}
    }
    static NotificationCompat.Builder notification(Context c, String channel, String title, String body) {
        PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(c, channel).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true);
    }
}
