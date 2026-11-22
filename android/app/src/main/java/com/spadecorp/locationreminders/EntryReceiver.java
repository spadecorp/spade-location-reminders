package com.spadecorp.locationreminders;

import android.app.NotificationManager;
import android.content.*;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.*;
import org.json.*;
import java.util.*;

public class EntryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        GeofencingEvent event=GeofencingEvent.fromIntent(intent);
        if (event==null) return;
        if (event.hasError()) {
            Fences.health(c).edit().putString("error","Monitoring interrupted. Check device location settings and reopen the app.").apply();
            return;
        }
        if (event.getGeofenceTransition()!=Geofence.GEOFENCE_TRANSITION_ENTER || event.getTriggeringGeofences()==null) return;
        Fences.channels(c);
        if (!Fences.canNotify(c)) return;
        ArrayList<String> lines=new ArrayList<>();
        for (Geofence fence:event.getTriggeringGeofences()) {
            String id=fence.getRequestId(), raw=Fences.prefs(c).getString(id,null);
            if (raw==null) continue;
            try {
                JSONObject row=new JSONObject(raw);
                String task=row.getString("task");
                c.getSystemService(NotificationManager.class).notify(id,1,
                    Fences.notification(c,Fences.CHANNEL,"Location reminder",task)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).build());
                if (row.optBoolean("speak")) lines.add(task);
            } catch (JSONException ignored) {}
        }
        if (!lines.isEmpty()) {
            try {ContextCompat.startForegroundService(c,new Intent(c,SpeechService.class).putStringArrayListExtra("tasks",lines));}
            catch (IllegalStateException | SecurityException ignored) { /* The visible reminder was delivered. */ }
        }
    }
}
