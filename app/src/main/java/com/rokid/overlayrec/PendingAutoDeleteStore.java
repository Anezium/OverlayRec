package com.rokid.overlayrec;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PendingAutoDeleteStore {
    private static final String TAG = "PendingAutoDelete";
    private static final String PREF_JOBS = "pending_auto_delete_jobs_v2";

    private final SharedPreferences prefs;

    PendingAutoDeleteStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    PendingAutoDeleteJob addJob(List<HudVideoItem> videos, long dueAt) {
        ArrayList<String> uris = new ArrayList<>();
        for (HudVideoItem item : videos) {
            uris.add(item.uri.toString());
        }
        PendingAutoDeleteJob job = new PendingAutoDeleteJob(
                UUID.randomUUID().toString(),
                dueAt,
                uris);
        ArrayList<PendingAutoDeleteJob> jobs = new ArrayList<>(jobs());
        jobs.add(job);
        save(jobs);
        return job;
    }

    List<PendingAutoDeleteJob> jobs() {
        ArrayList<PendingAutoDeleteJob> jobs = new ArrayList<>();
        String raw = prefs.getString(PREF_JOBS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                JSONArray uriArray = object.getJSONArray("uris");
                ArrayList<String> uris = new ArrayList<>();
                for (int j = 0; j < uriArray.length(); j++) {
                    uris.add(uriArray.getString(j));
                }
                jobs.add(new PendingAutoDeleteJob(
                        object.getString("id"),
                        object.getLong("dueAt"),
                        uris));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Dropping corrupt pending delete queue", e);
            save(jobs);
        }
        return jobs;
    }

    PendingAutoDeleteJob findJob(String id) {
        for (PendingAutoDeleteJob job : jobs()) {
            if (job.id.equals(id)) {
                return job;
            }
        }
        return null;
    }

    List<PendingAutoDeleteJob> dueJobs(long now) {
        ArrayList<PendingAutoDeleteJob> due = new ArrayList<>();
        for (PendingAutoDeleteJob job : jobs()) {
            if (job.dueAt <= now) {
                due.add(job);
            }
        }
        return due;
    }

    long nextDelayMs(long now) {
        long nextDueAt = Long.MAX_VALUE;
        for (PendingAutoDeleteJob job : jobs()) {
            if (job.dueAt < nextDueAt) {
                nextDueAt = job.dueAt;
            }
        }
        return nextDueAt == Long.MAX_VALUE ? -1L : Math.max(0L, nextDueAt - now);
    }

    void removeJob(String id) {
        ArrayList<PendingAutoDeleteJob> kept = new ArrayList<>();
        for (PendingAutoDeleteJob job : jobs()) {
            if (!job.id.equals(id)) {
                kept.add(job);
            }
        }
        save(kept);
    }

    void deferJob(String id, long dueAt) {
        ArrayList<PendingAutoDeleteJob> updated = new ArrayList<>();
        for (PendingAutoDeleteJob job : jobs()) {
            if (job.id.equals(id)) {
                updated.add(new PendingAutoDeleteJob(job.id, dueAt, job.uriStrings));
            } else {
                updated.add(job);
            }
        }
        save(updated);
    }

    private void save(List<PendingAutoDeleteJob> jobs) {
        JSONArray array = new JSONArray();
        try {
            for (PendingAutoDeleteJob job : jobs) {
                JSONObject object = new JSONObject();
                object.put("id", job.id);
                object.put("dueAt", job.dueAt);
                JSONArray uris = new JSONArray();
                for (String uri : job.uriStrings) {
                    uris.put(uri);
                }
                object.put("uris", uris);
                array.put(object);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Failed to save pending delete queue", e);
        }
        prefs.edit().putString(PREF_JOBS, array.toString()).apply();
    }
}
