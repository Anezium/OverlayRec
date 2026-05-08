package com.rokid.overlayrec;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class RokidArCommands {
    static final String ACTION_CMD = "com.rokid.os.master.assist.server.cmd";
    static final String SCENE_AR_PICTURE = "ar_picture";
    static final String SCENE_MIX_RECORD = "mix_record";

    private static final String TAG = "OverlayRecCmd";

    private RokidArCommands() {
    }

    static void startArScreenshot(Context context) {
        sendScene(context, SCENE_AR_PICTURE, true);
    }

    static void startArRecord(Context context) {
        sendScene(context, SCENE_MIX_RECORD, true);
    }

    private static void sendScene(Context context, String scene, boolean open) {
        Intent intent = new Intent(ACTION_CMD);
        intent.putExtra("cmd_type", "control_scene");
        intent.putExtra("scene", scene);
        intent.putExtra("open", open ? "true" : "false");
        context.sendBroadcast(intent);
        Log.i(TAG, "Sent scene=" + scene + " open=" + open);
    }
}
