package com.rokid.overlayrec;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;

public class OverlayRecService extends AccessibilityService {
    private static final String TAG = "OverlayRecSvc";

    private static final String ACTION_TWO_FINGER_SWIPE_FORWARD =
            "com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD";
    private static final String ACTION_TWO_FINGER_SWIPE_BACK =
            "com.android.action.ACTION_TWO_FINGER_SWIPE_BACK";

    private static final long COMMAND_TIMEOUT_MS = 2200L;
    private static final long LAUNCH_DELAY_MS = 350L;

    private enum Direction { LEFT, RIGHT }
    enum Action { SCREENSHOT, RECORD }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Direction> inputBuffer = new ArrayList<>();
    private long lastInputAt = 0L;
    private AudioManager audioManager;
    private VolumeSnapshot commandVolume;
    private OverlayMenu overlayMenu;

    private final BroadcastReceiver twoFingerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_TWO_FINGER_SWIPE_BACK.equals(action)) {
                onTwoFinger(Direction.LEFT);
            } else if (ACTION_TWO_FINGER_SWIPE_FORWARD.equals(action)) {
                onTwoFinger(Direction.RIGHT);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        overlayMenu = new OverlayMenu(this, new OverlayMenu.Listener() {
            @Override
            public void onSelectionChanged() {
                restoreCommandVolumeSoon();
            }

            @Override
            public void onActionChosen(Action action) {
                runAction(action);
            }

            @Override
            public void onDismissed() {
                restoreCommandVolumeSoon();
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TWO_FINGER_SWIPE_BACK);
        filter.addAction(ACTION_TWO_FINGER_SWIPE_FORWARD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(twoFingerReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(twoFingerReceiver, filter);
        }
        Log.i(TAG, "Created and registered two-finger receiver");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }
        Log.i(TAG, "Accessibility connected");
    }

    @Override
    public void onDestroy() {
        overlayMenu.hide();
        try {
            unregisterReceiver(twoFingerReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Interrupted");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return false;
        }

        int keyCode = event.getKeyCode();
        Log.i(TAG, "key=" + KeyEvent.keyCodeToString(keyCode) + " code=" + keyCode);

        if (overlayMenu.isShowing()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                overlayMenu.confirmNow();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                overlayMenu.hide();
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return handleDirectionalFallback(Direction.LEFT);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return handleDirectionalFallback(Direction.RIGHT);
        }
        return false;
    }

    private boolean handleDirectionalFallback(Direction direction) {
        boolean matched = addToCommandBuffer(direction);
        if (matched) {
            showMenu();
            return true;
        }
        return false;
    }

    private void onTwoFinger(Direction direction) {
        Log.i(TAG, "two-finger " + direction);
        if (overlayMenu.isShowing()) {
            overlayMenu.select(direction == Direction.LEFT ? Action.SCREENSHOT : Action.RECORD);
            restoreCommandVolumeSoon();
            return;
        }

        if (commandVolume == null) {
            commandVolume = VolumeSnapshot.capture(audioManager);
        }
        if (addToCommandBuffer(direction)) {
            showMenu();
        }
    }

    private boolean addToCommandBuffer(Direction direction) {
        long now = System.currentTimeMillis();
        if (now - lastInputAt > COMMAND_TIMEOUT_MS) {
            inputBuffer.clear();
            commandVolume = null;
        }
        lastInputAt = now;

        inputBuffer.add(direction);
        if (inputBuffer.size() > 8) {
            inputBuffer.remove(0);
        }

        int size = inputBuffer.size();
        return size >= 4
                && inputBuffer.get(size - 4) == Direction.LEFT
                && inputBuffer.get(size - 3) == Direction.LEFT
                && inputBuffer.get(size - 2) == Direction.RIGHT
                && inputBuffer.get(size - 1) == Direction.RIGHT;
    }

    private void showMenu() {
        Log.i(TAG, "combo matched, showing menu");
        inputBuffer.clear();
        restoreCommandVolumeSoon();
        overlayMenu.show();
    }

    private void runAction(Action action) {
        Log.i(TAG, "running action " + action);
        overlayMenu.hide();
        restoreCommandVolumeSoon();
        handler.postDelayed(() -> {
            if (action == Action.SCREENSHOT) {
                RokidArCommands.startArScreenshot(this);
            } else {
                RokidArCommands.startArRecord(this);
            }
        }, LAUNCH_DELAY_MS);
    }

    private void restoreCommandVolumeSoon() {
        VolumeSnapshot snapshot = commandVolume;
        if (snapshot == null) return;
        handler.postDelayed(() -> snapshot.restore(audioManager), 80L);
        handler.postDelayed(() -> {
            snapshot.restore(audioManager);
            if (!overlayMenu.isShowing()) {
                commandVolume = null;
            }
        }, 300L);
    }

    private static final class VolumeSnapshot {
        private final int music;
        private final int system;

        private VolumeSnapshot(int music, int system) {
            this.music = music;
            this.system = system;
        }

        static VolumeSnapshot capture(AudioManager audioManager) {
            if (audioManager == null) return null;
            return new VolumeSnapshot(
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                    audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM));
        }

        void restore(AudioManager audioManager) {
            if (audioManager == null) return;
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, music, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, system, 0);
            } catch (SecurityException e) {
                Log.w(TAG, "Volume restore blocked: " + e.getMessage());
            }
        }
    }
}
