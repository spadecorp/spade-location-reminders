package com.spadecorp.locationreminders;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle state) {
        registerPlugin(RemindersPlugin.class);
        super.onCreate(state);
    }
}
