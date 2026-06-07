package com.rokid.overlayrec;

import android.net.Uri;

final class HudVideoItem {
    final long id;
    final String name;
    final long size;
    final long modified;
    final Uri uri;
    final String path;

    HudVideoItem(long id, String name, long size, long modified, Uri uri, String path) {
        this.id = id;
        this.name = name;
        this.size = size;
        this.modified = modified;
        this.uri = uri;
        this.path = path;
    }
}
