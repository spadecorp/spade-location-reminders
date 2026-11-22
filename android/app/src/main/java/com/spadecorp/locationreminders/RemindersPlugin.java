package com.spadecorp.locationreminders;

import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import com.google.android.gms.location.LocationServices;
import java.util.*;
import org.json.*;

@CapacitorPlugin(name="Reminders", permissions={
    @Permission(alias="notify",strings={Manifest.permission.POST_NOTIFICATIONS}),
    @Permission(alias="background",strings={Manifest.permission.ACCESS_BACKGROUND_LOCATION})
})
public class RemindersPlugin extends Plugin {
    private boolean restored;
    @Override public void load() {Fences.channels(getContext());}
    @PluginMethod public void list(PluginCall call) {
        Context c = getContext();
        Runnable respond = () -> {
            JSArray rows = new JSArray();
            for (JSONObject item : Fences.items(c)) rows.put(item);
            String warning = "";
            if (rows.length() > 0) {
                if (!Fences.canLocate(c)) warning="Arrival alerts need precise location and background access. Open a reminder and save it again to restore permissions.";
                else if (!Fences.canNotify(c)) warning="Notifications are disabled. Enable Arrival reminders in app settings.";
                else warning=Fences.health(c).getString("error","");
            }
            call.resolve(new JSObject().put("items", rows).put("warning",warning));
        };
        if (!Fences.canLocate(c)) restored = false;
        if (restored || !Fences.canLocate(c)) {respond.run();return;}
        Fences.restore(c, () -> {
            restored = Fences.health(c).getString("error","").isEmpty();
            respond.run();
        });
    }
    @PluginMethod public void save(PluginCall call) {
        if (Build.VERSION.SDK_INT>=33 && !Fences.granted(getContext(),Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermissionForAlias("notify",call,"afterNotify");return;
        }
        afterNotify(call);
    }
    @PermissionCallback private void afterNotify(PluginCall call) {
        if (!Fences.canNotify(getContext())) {call.reject("Enable notifications for Arrival reminders in settings.");return;}
        if (Build.VERSION.SDK_INT==29 && !Fences.canLocate(getContext())) {
            requestPermissionForAlias("background",call,"schedule");return;
        }
        schedule(call);
    }
    @PermissionCallback private void schedule(PluginCall call) {
        Context c = getContext();
        if (!Fences.canLocate(c)) {call.reject("In app settings, open Permissions → Location and choose Allow all the time with precise location enabled. Return here and tap Save reminder.");return;}
        try {
            JSONObject row=call.getData();
            String id=row.getString("id");
            UUID.fromString(id);
            String task=row.getString("task").trim();
            int radius=row.getInt("radius");
            if (task.isEmpty() || task.length()>120 || radius<100 || radius>3000) throw new IllegalArgumentException();
            row.getBoolean("speak");
            Fences.register(c, Collections.singletonList(Fences.fence(row))).addOnSuccessListener(unused->{
                Fences.prefs(c).edit().putString(id,row.toString()).apply();
                call.resolve();
            }).addOnFailureListener(e->call.reject(Fences.error(e)));
        } catch(Exception e){call.reject("Invalid task, coordinates or radius.");}
    }
    @PluginMethod public void remove(PluginCall call) {
        String id=call.getString("id");
        if(id==null){call.reject("Missing reminder ID.");return;}
        Context c=getContext();
        // Removal from the local store also suppresses late, already queued transitions.
        Runnable finish=()->{
            Fences.prefs(c).edit().remove(id).apply();
            c.getSystemService(android.app.NotificationManager.class).cancel(id,1);
            call.resolve();
        };
        if(!Fences.canLocate(c)){finish.run();return;}
        LocationServices.getGeofencingClient(c).removeGeofences(Collections.singletonList(id))
            .addOnSuccessListener(unused->finish.run()).addOnFailureListener(e->call.reject("Could not remove the geofence. Please retry."));
    }
    @PluginMethod public void settings(PluginCall call) {
        getActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getContext().getPackageName())));
        call.resolve();
    }
}
