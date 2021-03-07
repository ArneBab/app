package org.freenetproject.mobile.services.node;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.IBinder;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.freenetproject.mobile.proxy.Simple;
import org.freenetproject.mobile.receivers.BatteryLevelReceiver;
import org.freenetproject.mobile.receivers.PowerConnectionReceiver;
import org.freenetproject.mobile.ui.notification.Notification;

public class Service extends android.app.Service {
    PowerConnectionReceiver powerConnectionReceiver = new PowerConnectionReceiver();
    ConnectivityManager connectivityManager;
    ConnectivityManager.NetworkCallback networkCallback;
    Thread proxyThread = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("Freenet", "Called service onStartCommand");

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Boolean preserveBattery = sharedPreferences.getBoolean("preserve_battery", true);

        // Register power connection receiver
        if (preserveBattery) {
            IntentFilter powerConnectedFilter = new IntentFilter();
            powerConnectedFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            powerConnectedFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            registerReceiver(powerConnectionReceiver, powerConnectedFilter);

            registerReceiver(new BatteryLevelReceiver(), new IntentFilter(
                    Intent.ACTION_BATTERY_LOW));
        }

        Boolean preserveData = sharedPreferences.getBoolean("preserve_data",true);
        // Register network callback
        if (preserveData) {
            Context context = getApplicationContext();
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();

            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d("Freenet", "Resuming service from network change");
                    Manager.getInstance().resumeService(context, Manager.CONTEXT_NETWORK);
                }

                @Override
                public void onLost(Network network) {
                    Log.d("Freenet", "Pausing service from network change");
                    Manager.getInstance().pauseService(context, Manager.CONTEXT_NETWORK);
                }
            };

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }

        Boolean webAccess = sharedPreferences.getBoolean("web_access",false);
        if (webAccess) {
            if (proxyThread != null) proxyThread.interrupt();
            proxyThread = new Thread(new Simple(), "simple-proxy-server");
            proxyThread.start();
        }

        startForeground(1, Notification.show(this));

        return android.app.Service.START_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.i("Freenet", "Stopping service");
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        Boolean preserveBattery = sharedPreferences.getBoolean("preserve_battery", true);
        if (preserveBattery) unregisterReceiver(powerConnectionReceiver);

        Boolean preserveData = sharedPreferences.getBoolean("preserve_data",true);
        if (preserveData) connectivityManager.unregisterNetworkCallback(networkCallback);

        Boolean webAccess = sharedPreferences.getBoolean("web_access",false);
        if (webAccess) {
            if (proxyThread != null) proxyThread.interrupt();
        }

        Notification.remove(getApplicationContext());
        stopForeground(true);
        stopSelf();
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }
}
