package com.rokid.overlayrec;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class RokidScreenRecordCommands {
    static final String PACKAGE_SCREENSTREAM = "com.rokid.os.master.screenstream";
    static final String ACTION_SCREENRECORD_ON = "com.rokid.yodaos.action.SCREENRECORD_ON";
    static final String ACTION_SCREENRECORD_OFF = "com.rokid.yodaos.action.SCREENRECORD_OFF";
    static final String ACTION_SCREENRECORD_START = "com.rokid.yodaos.action.SCREENRECORD_START";
    static final String ACTION_SCREENRECORD_STOP = "com.rokid.yodaos.action.SCREENRECORD_STOP";

    private static final String TAG = "OverlayRecScreen";

    private RokidScreenRecordCommands() {
    }

    static void startHudRecord(Context context) {
        sendScreenRecordBroadcast(context, ACTION_SCREENRECORD_ON);
    }

    static void stopHudRecord(Context context) {
        sendScreenRecordBroadcast(context, ACTION_SCREENRECORD_OFF);
    }

    private static void sendScreenRecordBroadcast(Context context, String action) {
        Intent intent = new Intent(action);
        intent.setPackage(PACKAGE_SCREENSTREAM);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
        Log.i(TAG, "Sent " + action);
    }
}
