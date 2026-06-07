package com.rokid.overlayrec;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

final class PendingAutoDeleteJob {
    final String id;
    final long dueAt;
    final ArrayList<String> uriStrings;

    PendingAutoDeleteJob(String id, long dueAt, List<String> uriStrings) {
        this.id = id;
        this.dueAt = dueAt;
        this.uriStrings = new ArrayList<>(uriStrings);
    }

    boolean contains(Uri uri) {
        return uriStrings.contains(uri.toString());
    }
}
