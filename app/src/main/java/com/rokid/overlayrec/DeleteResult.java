package com.rokid.overlayrec;

import android.net.Uri;

import java.util.ArrayList;

final class DeleteResult {
    final int deleted;
    final ArrayList<Uri> failedUris;

    DeleteResult(int deleted, ArrayList<Uri> failedUris) {
        this.deleted = deleted;
        this.failedUris = failedUris;
    }

    boolean hasFailures() {
        return !failedUris.isEmpty();
    }
}
