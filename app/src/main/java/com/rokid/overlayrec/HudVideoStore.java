package com.rokid.overlayrec;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HudVideoStore {
    private static final String TAG = "HudVideoStore";

    private final Context context;

    HudVideoStore(Context context) {
        this.context = context.getApplicationContext();
    }

    List<HudVideoItem> listVideos() {
        ArrayList<HudVideoItem> videos = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.DATA
        };
        String selection = null;
        String[] selectionArgs = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs = new String[]{"%ScreenRecorder%"};
        }

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                MediaStore.Video.Media.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return videos;
            }

            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
            int modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED);
            int pathIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (name == null || !name.toLowerCase(Locale.US).endsWith(".mp4")) {
                    continue;
                }

                long id = cursor.getLong(idIndex);
                long size = cursor.getLong(sizeIndex);
                long modified = cursor.getLong(modifiedIndex) * 1000L;
                String path = pathIndex >= 0 ? cursor.getString(pathIndex) : null;
                Uri uri = ContentUris.withAppendedId(collection, id);
                videos.add(new HudVideoItem(id, name, size, modified, uri, path));
            }
        }
        return videos;
    }

    DeleteResult deleteWithoutPrompt(List<HudVideoItem> items) {
        ArrayList<Uri> failed = new ArrayList<>();
        int deleted = 0;
        ContentResolver resolver = context.getContentResolver();
        for (HudVideoItem item : items) {
            boolean deletedItem = false;
            try {
                deletedItem = resolver.delete(item.uri, null, null) > 0;
            } catch (SecurityException e) {
                Log.i(TAG, "MediaStore delete needs confirmation for " + item.name);
            }

            if (!deletedItem) {
                deletedItem = deleteFileDirectly(item);
            }

            if (deletedItem) {
                deleted += 1;
            } else {
                failed.add(item.uri);
            }
        }
        return new DeleteResult(deleted, failed);
    }

    private boolean deleteFileDirectly(HudVideoItem item) {
        if (item.path == null || item.path.trim().isEmpty()) {
            return false;
        }
        try {
            File file = new File(item.path);
            if (!file.exists()) {
                return false;
            }
            boolean deleted = file.delete();
            if (deleted) {
                Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scan.setData(Uri.fromFile(file));
                context.sendBroadcast(scan);
            }
            return deleted;
        } catch (SecurityException e) {
            Log.i(TAG, "File delete blocked for " + item.name);
            return false;
        }
    }
}
