package com.onessh.app;

import android.app.Application;

import com.onessh.app.data.Store;
import com.onessh.app.ssh.KnownHosts;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        KnownHosts.init(Store.get(this));
    }
}
