package com.rokid.overlayrec;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HudVideoActivity extends Activity {
    private static final int REQUEST_READ_VIDEO = 41;
    private static final int REQUEST_DELETE_VIDEO = 42;
    private static final int REQUEST_AUTO_DELETE_VIDEO = 43;
    private static final long AUTO_DELETE_DELAY_MS = 10L * 60L * 1000L;
    private static final long AUTO_DELETE_RETRY_DELAY_MS = 60L * 1000L;
    private static final String PREFS = "hud_video_library";
    private static final String PREF_AUTO_DELETE = "auto_delete_after_bt";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RokidKeyNavigation keyNavigation = new RokidKeyNavigation();
    private final FocusListState focus = new FocusListState();
    private final ArrayList<HudVideoItem> videos = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();

    private HudLibraryView libraryView;
    private SharedPreferences prefs;
    private HudVideoStore videoStore;
    private BluetoothVideoShare videoShare;
    private PendingAutoDeleteStore pendingDeleteStore;
    private ActiveDeleteRequest activeDeleteRequest;
    private int firstVisibleVideoIndex;
    private String statusText = "Loading videos...";

    private final Runnable pendingAutoDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            runDueAutoDeletes();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        videoStore = new HudVideoStore(this);
        videoShare = new BluetoothVideoShare();
        pendingDeleteStore = new PendingAutoDeleteStore(prefs);

        libraryView = new HudLibraryView();
        setContentView(libraryView);
        libraryView.requestFocus();

        if (hasReadPermission()) {
            refreshVideos();
            runDueAutoDeletes();
        } else {
            updateStatus("Grant video access.");
            requestReadPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (libraryView != null) {
            libraryView.requestFocus();
        }
        if (hasReadPermission()) {
            refreshVideos();
            runDueAutoDeletes();
            scheduleNextAutoDelete();
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(pendingAutoDeleteRunnable);
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return keyNavigation.dispatch(event, new RokidKeyNavigation.Listener() {
            @Override
            public void onNext() {
                moveFocus(1);
            }

            @Override
            public void onPrevious() {
                moveFocus(-1);
            }

            @Override
            public void onConfirm() {
                activateFocusedItem();
            }

            @Override
            public void onBack() {
                finish();
            }
        }) || super.dispatchKeyEvent(event);
    }

    private void moveFocus(int delta) {
        syncFocusCount();
        focus.move(delta);
        ensureFocusedVideoVisible();
        updateStatusForFocus();
    }

    private void activateFocusedItem() {
        int selectedIndex = focus.selectedIndex();
        if (selectedIndex < videos.size()) {
            toggleSelected(videos.get(selectedIndex));
            return;
        }

        LibraryAction action = LibraryAction.values()[selectedIndex - videos.size()];
        if (action == LibraryAction.SEND) {
            sendSelected();
        } else if (action == LibraryAction.DELETE) {
            deleteSelected();
        } else if (action == LibraryAction.REFRESH) {
            refreshVideos();
        } else if (action == LibraryAction.AUTO_DELETE) {
            toggleAutoDelete();
        } else if (action == LibraryAction.BACK) {
            finish();
        }
    }

    private void refreshVideos() {
        if (!loadVideos()) {
            return;
        }
        syncSelection();
        syncFocusCount();
        ensureFocusedVideoVisible();
        updateStatusForFocus();
    }

    private boolean loadVideos() {
        videos.clear();
        if (!hasReadPermission()) {
            updateStatus("Grant video access.");
            return false;
        }
        try {
            videos.addAll(videoStore.listVideos());
            return true;
        } catch (SecurityException e) {
            requestReadPermission();
            return false;
        }
    }

    private void syncSelection() {
        HashSet<Long> ids = new HashSet<>();
        for (HudVideoItem item : videos) {
            ids.add(item.id);
        }
        selectedIds.retainAll(ids);
    }

    private void syncFocusCount() {
        focus.setItemCount(videos.size() + LibraryAction.values().length);
    }

    private void ensureFocusedVideoVisible() {
        int selectedIndex = focus.selectedIndex();
        if (libraryView == null || selectedIndex >= videos.size()) {
            return;
        }

        int visibleRows = libraryView.visibleVideoRows();
        if (selectedIndex < firstVisibleVideoIndex) {
            firstVisibleVideoIndex = selectedIndex;
        } else if (selectedIndex >= firstVisibleVideoIndex + visibleRows) {
            firstVisibleVideoIndex = selectedIndex - visibleRows + 1;
        }

        int maxFirst = Math.max(0, videos.size() - visibleRows);
        if (firstVisibleVideoIndex > maxFirst) {
            firstVisibleVideoIndex = maxFirst;
        }
    }

    private void toggleSelected(HudVideoItem item) {
        if (selectedIds.contains(item.id)) {
            selectedIds.remove(item.id);
        } else {
            selectedIds.add(item.id);
        }
        updateStatusForFocus();
    }

    private List<HudVideoItem> selectedVideos() {
        ArrayList<HudVideoItem> selected = new ArrayList<>();
        for (HudVideoItem item : videos) {
            if (selectedIds.contains(item.id)) {
                selected.add(item);
            }
        }
        return selected;
    }

    private void sendSelected() {
        List<HudVideoItem> selected = selectedVideos();
        if (selected.isEmpty()) {
            updateStatus("Select videos first.");
            return;
        }

        try {
            videoShare.send(this, selected);
            if (isAutoDeleteEnabled()) {
                long dueAt = System.currentTimeMillis() + AUTO_DELETE_DELAY_MS;
                pendingDeleteStore.addJob(selected, dueAt);
                scheduleNextAutoDelete();
                updateStatus("Bluetooth opened. Auto-delete in 10 min.");
            } else {
                updateStatus("Bluetooth opened for " + selected.size() + " video(s).");
            }
        } catch (Exception e) {
            updateStatus("Bluetooth failed: " + e.getClass().getSimpleName());
        }
    }

    private void deleteSelected() {
        List<HudVideoItem> selected = selectedVideos();
        if (selected.isEmpty()) {
            updateStatus("Select videos to delete.");
            return;
        }

        DeleteResult result = videoStore.deleteWithoutPrompt(selected);
        if (result.hasFailures()) {
            activeDeleteRequest = ActiveDeleteRequest.manual();
            if (requestDeletePrompt(result.failedUris, REQUEST_DELETE_VIDEO, "Confirm delete prompt.")) {
                return;
            }
        }

        refreshVideos();
        updateStatus(result.hasFailures()
                ? "Deleted " + result.deleted + ". Confirmation needed."
                : "Deleted " + result.deleted + " video(s).");
    }

    private void runDueAutoDeletes() {
        if (!hasReadPermission()) {
            return;
        }
        if (!loadVideos()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean changed = false;
        for (PendingAutoDeleteJob job : pendingDeleteStore.dueJobs(now)) {
            List<HudVideoItem> jobVideos = videosForJob(job);
            if (jobVideos.isEmpty()) {
                pendingDeleteStore.removeJob(job.id);
                changed = true;
                continue;
            }

            DeleteResult result = videoStore.deleteWithoutPrompt(jobVideos);
            if (result.hasFailures()) {
                activeDeleteRequest = ActiveDeleteRequest.auto(job.id);
                if (requestDeletePrompt(
                        result.failedUris,
                        REQUEST_AUTO_DELETE_VIDEO,
                        "Confirm auto-delete prompt.")) {
                    return;
                }
                pendingDeleteStore.deferJob(job.id, now + AUTO_DELETE_RETRY_DELAY_MS);
            } else {
                pendingDeleteStore.removeJob(job.id);
            }
            changed = true;
        }

        if (changed) {
            refreshVideos();
        }
        scheduleNextAutoDelete();
    }

    private List<HudVideoItem> videosForJob(PendingAutoDeleteJob job) {
        ArrayList<HudVideoItem> jobVideos = new ArrayList<>();
        for (HudVideoItem item : videos) {
            if (job.contains(item.uri)) {
                jobVideos.add(item);
            }
        }
        return jobVideos;
    }

    private void scheduleNextAutoDelete() {
        handler.removeCallbacks(pendingAutoDeleteRunnable);
        long delay = pendingDeleteStore.nextDelayMs(System.currentTimeMillis());
        if (delay >= 0L) {
            handler.postDelayed(pendingAutoDeleteRunnable, delay);
        }
    }

    private void resolveAutoDeleteResult(String jobId) {
        refreshVideos();
        PendingAutoDeleteJob job = pendingDeleteStore.findJob(jobId);
        if (job == null) {
            scheduleNextAutoDelete();
            return;
        }

        if (videosForJob(job).isEmpty()) {
            pendingDeleteStore.removeJob(job.id);
            updateStatus("Auto-delete complete.");
        } else {
            pendingDeleteStore.deferJob(job.id, System.currentTimeMillis() + AUTO_DELETE_RETRY_DELAY_MS);
            updateStatus("Auto-delete still pending.");
        }
        scheduleNextAutoDelete();
    }

    private boolean requestDeletePrompt(ArrayList<Uri> uris, int requestCode, String status) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            PendingIntent request = MediaStore.createDeleteRequest(getContentResolver(), uris);
            startIntentSenderForResult(
                    request.getIntentSender(),
                    requestCode,
                    null,
                    0,
                    0,
                    0);
            updateStatus(status);
            return true;
        } catch (IntentSender.SendIntentException e) {
            return false;
        }
    }

    private void toggleAutoDelete() {
        boolean enabled = !isAutoDeleteEnabled();
        prefs.edit().putBoolean(PREF_AUTO_DELETE, enabled).apply();
        updateStatus(enabled ? "Auto-delete ON after BT." : "Auto-delete OFF.");
    }

    private boolean isAutoDeleteEnabled() {
        return prefs.getBoolean(PREF_AUTO_DELETE, false);
    }

    private boolean hasReadPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_READ_VIDEO);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_VIDEO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_VIDEO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshVideos();
            runDueAutoDeletes();
        } else {
            updateStatus("Video access denied.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActiveDeleteRequest request = activeDeleteRequest;
        activeDeleteRequest = null;

        if (requestCode == REQUEST_AUTO_DELETE_VIDEO && request != null && request.pendingJobId != null) {
            resolveAutoDeleteResult(request.pendingJobId);
        } else if (requestCode == REQUEST_DELETE_VIDEO || requestCode == REQUEST_AUTO_DELETE_VIDEO) {
            refreshVideos();
        }
    }

    private void updateStatusForFocus() {
        int selectedIndex = focus.selectedIndex();
        if (selectedIndex < videos.size()) {
            updateStatus((selectedIndex + 1) + "/" + videos.size()
                    + " videos - "
                    + selectedIds.size()
                    + " selected");
            return;
        }

        updateStatus(videos.size()
                + " videos - "
                + selectedIds.size()
                + " selected - "
                + actionLabel(LibraryAction.values()[selectedIndex - videos.size()]));
    }

    private void updateStatus(String status) {
        statusText = status;
        invalidateLibrary();
    }

    private void invalidateLibrary() {
        if (libraryView != null) {
            libraryView.invalidate();
        }
    }

    private String actionLabel(LibraryAction action) {
        if (action == LibraryAction.AUTO_DELETE) {
            return isAutoDeleteEnabled() ? "Auto DELETE : ON" : "Auto DELETE : OFF";
        }
        return action.label;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
        }
        return String.format(Locale.US, "%.0f KB", bytes / 1024f);
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(millis));
    }

    private enum LibraryAction {
        SEND("Send"),
        DELETE("Delete"),
        REFRESH("Refresh"),
        AUTO_DELETE("Auto DELETE : OFF"),
        BACK("Back");

        final String label;

        LibraryAction(String label) {
            this.label = label;
        }
    }

    private static final class ActiveDeleteRequest {
        final String pendingJobId;

        private ActiveDeleteRequest(String pendingJobId) {
            this.pendingJobId = pendingJobId;
        }

        static ActiveDeleteRequest manual() {
            return new ActiveDeleteRequest(null);
        }

        static ActiveDeleteRequest auto(String pendingJobId) {
            return new ActiveDeleteRequest(pendingJobId);
        }
    }

    private final class HudLibraryView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        HudLibraryView() {
            super(HudVideoActivity.this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setBackgroundColor(Color.BLACK);
            strokePaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);

            int width = getWidth();
            int height = getHeight();
            int side = dp(18);
            int titleBaseline = dp(35);
            int statusBaseline = dp(61);
            int listTop = dp(82);
            int actionTop = actionAreaTop();
            int listBottom = Math.max(listTop, actionTop - dp(10));

            drawCenteredText(canvas, "HUD Videos", titleBaseline, sp(23), Color.rgb(53, 215, 166));
            drawCenteredText(canvas, statusText, statusBaseline, sp(12), Color.rgb(218, 226, 234));

            drawVideoRows(canvas, side, listTop, width - side, listBottom);
            drawActions(canvas, side, actionTop, width - side, height - dp(12));
        }

        int visibleVideoRows() {
            int available = Math.max(0, actionAreaTop() - dp(10) - dp(82));
            return Math.max(1, available / rowStride());
        }

        private int actionAreaTop() {
            return Math.max(dp(462), getHeight() - dp(152));
        }

        private int rowStride() {
            return dp(66);
        }

        private void drawVideoRows(Canvas canvas, int left, int top, int right, int bottom) {
            if (videos.isEmpty()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setColor(Color.rgb(64, 78, 90));
                rect.set(left, top + dp(8), right, Math.min(bottom, top + dp(98)));
                canvas.drawRoundRect(rect, dp(6), dp(6), paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.WHITE);
                paint.setTextSize(sp(17));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("No HUD videos", getWidth() / 2f, top + dp(45), paint);
                paint.setColor(Color.rgb(174, 188, 200));
                paint.setTextSize(sp(12));
                canvas.drawText("Record first, then refresh.", getWidth() / 2f, top + dp(70), paint);
                return;
            }

            ensureFocusedVideoVisible();
            int end = Math.min(videos.size(), firstVisibleVideoIndex + visibleVideoRows());
            for (int index = firstVisibleVideoIndex; index < end; index++) {
                int rowTop = top + (index - firstVisibleVideoIndex) * rowStride();
                drawVideoRow(canvas, videos.get(index), index, left, rowTop, right);
            }
        }

        private void drawVideoRow(Canvas canvas, HudVideoItem item, int index, int left, int top, int right) {
            boolean focused = focus.selectedIndex() == index;
            boolean selected = selectedIds.contains(item.id);
            int bottom = top + dp(58);

            strokePaint.setStrokeWidth(focused ? dp(3) : dp(1));
            strokePaint.setColor(focused
                    ? Color.rgb(53, 215, 166)
                    : selected ? Color.rgb(112, 214, 184) : Color.rgb(64, 78, 90));
            rect.set(left, top + dp(3), right, bottom);
            canvas.drawRoundRect(rect, dp(6), dp(6), strokePaint);

            int checkLeft = left + dp(12);
            int checkTop = top + dp(20);
            rect.set(checkLeft, checkTop, checkLeft + dp(16), checkTop + dp(16));
            strokePaint.setStrokeWidth(dp(2));
            strokePaint.setColor(selected ? Color.rgb(53, 215, 166) : Color.rgb(138, 152, 164));
            canvas.drawRect(rect, strokePaint);
            if (selected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(53, 215, 166));
                canvas.drawRect(checkLeft + dp(4), checkTop + dp(4), checkLeft + dp(12), checkTop + dp(12), paint);
            }

            int textLeft = left + dp(40);
            int textRight = right - dp(12);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(13));
            paint.setColor(focused ? Color.WHITE : Color.rgb(228, 234, 240));
            canvas.drawText(ellipsize(item.name, textRight - textLeft), textLeft, top + dp(24), paint);

            paint.setTextSize(sp(11));
            paint.setColor(Color.rgb(164, 180, 192));
            canvas.drawText(formatSize(item.size) + "  " + formatDate(item.modified), textLeft, top + dp(45), paint);
        }

        private void drawActions(Canvas canvas, int left, int top, int right, int bottom) {
            int gap = dp(8);
            int cols = 3;
            int rowHeight = dp(44);
            int colWidth = (right - left - gap * (cols - 1)) / cols;
            LibraryAction[] actions = LibraryAction.values();

            for (int index = 0; index < actions.length; index++) {
                int row = index / cols;
                int col = index % cols;
                int actionLeft = left + col * (colWidth + gap);
                int actionTop = top + row * (rowHeight + gap);
                int actionRight = actionLeft + colWidth;
                int actionBottom = Math.min(bottom, actionTop + rowHeight);
                boolean focused = focus.selectedIndex() == videos.size() + index;

                strokePaint.setStrokeWidth(focused ? dp(3) : dp(1));
                strokePaint.setColor(focused ? Color.rgb(53, 215, 166) : Color.rgb(94, 110, 124));
                rect.set(actionLeft, actionTop, actionRight, actionBottom);
                canvas.drawRoundRect(rect, dp(6), dp(6), strokePaint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(sp(actions[index] == LibraryAction.AUTO_DELETE ? 11 : 13));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(focused ? Color.WHITE : Color.rgb(218, 226, 234));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float baseline = actionTop + (rowHeight - metrics.bottom + metrics.top) / 2f - metrics.top;
                canvas.drawText(actionLabel(actions[index]), (actionLeft + actionRight) / 2f, baseline, paint);
            }
        }

        private void drawCenteredText(Canvas canvas, String text, int baseline, int size, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(size);
            paint.setColor(color);
            canvas.drawText(text, getWidth() / 2f, baseline, paint);
        }

        private String ellipsize(String text, float availableWidth) {
            if (paint.measureText(text) <= availableWidth) {
                return text;
            }

            String ellipsis = "...";
            float ellipsisWidth = paint.measureText(ellipsis);
            int end = text.length();
            while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > availableWidth) {
                end--;
            }
            return text.substring(0, end) + ellipsis;
        }

        private float hudScale() {
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return 1f;
            }
            return Math.min(width / 480f, height / 640f);
        }

        private int dp(float value) {
            return Math.round(value * hudScale());
        }

        private int sp(float value) {
            return Math.round(value * hudScale());
        }
    }
}
