package nl.mpcjanssen.simpletask.remote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import nl.mpcjanssen.simpletask.TodoApplication;

/**
 * Created by Mark on 6/26/2015.
 */
public class ConnectivityReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        TodoApplication mApp = ((TodoApplication) context.getApplicationContext());
        FileStore store =  (FileStore) mApp.getFileStore();
        boolean connected = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        Log.v(TAG, "Connectivity changed");
        debugIntent(intent,TAG);
        store.changedConnectionState(connected);
        if (connected) {
            mApp.fileChanged();
        }
    }

    private void debugIntent(Intent intent, String tag) {
        Log.v(tag, "action: " + intent.getAction());
        Log.v(tag, "component: " + intent.getComponent());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key: extras.keySet()) {
                Log.v(tag, "key [" + key + "]: " +
                        extras.get(key));
            }
        }
        else {
            Log.v(tag, "no extras");
        }
    }
}