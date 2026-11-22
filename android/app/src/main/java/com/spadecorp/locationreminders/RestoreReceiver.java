package com.spadecorp.locationreminders;
import android.content.*;

public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult result = goAsync();
        Fences.restore(context, result::finish);
    }
}
