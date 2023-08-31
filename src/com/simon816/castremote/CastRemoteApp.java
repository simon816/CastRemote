package com.simon816.castremote;

import android.app.Application;

import java.util.UUID;

public class CastRemoteApp extends Application {

    public final String deviceId = UUID.randomUUID().toString();

}
