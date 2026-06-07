package com.rokid.overlayrec;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

final class BluetoothVideoShare {
    private static final String BLUETOOTH_PACKAGE = "com.android.bluetooth";

    void send(Activity activity, List<HudVideoItem> videos) {
        Intent intent = new Intent(videos.size() == 1
                ? Intent.ACTION_SEND
                : Intent.ACTION_SEND_MULTIPLE);
        intent.setPackage(BLUETOOTH_PACKAGE);
        intent.setType("video/mp4");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        ClipData clipData = ClipData.newUri(
                activity.getContentResolver(),
                videos.get(0).name,
                videos.get(0).uri);
        if (videos.size() == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, videos.get(0).uri);
        } else {
            ArrayList<Uri> uris = new ArrayList<>();
            for (HudVideoItem item : videos) {
                uris.add(item.uri);
            }
            for (int i = 1; i < uris.size(); i++) {
                clipData.addItem(new ClipData.Item(uris.get(i)));
            }
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.setClipData(clipData);
        activity.startActivity(intent);
    }
}
