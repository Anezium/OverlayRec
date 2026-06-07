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
import android.view.accessibility.AccessibilityNodeInfo;

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
    private static final long MODAL_INPUT_GUARD_MS = 700L;

    private enum Direction { LEFT, RIGHT }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Direction> inputBuffer = new ArrayList<>();
    private final RokidKeyNavigation modalNavigation = new RokidKeyNavigation();
    private long lastInputAt = 0L;
    private AudioManager audioManager;
    private VolumeSnapshot commandVolume;
    private OverlayMenu overlayMenu;
    private boolean hudRecording = false;
    private long modalInputGuardUntil = 0L;

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

    private final BroadcastReceiver screenRecordStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (RokidScreenRecordCommands.ACTION_SCREENRECORD_START.equals(action)) {
                hudRecording = true;
                overlayMenu.setHudRecording(true);
                Log.i(TAG, "HUD screen record started");
            } else if (RokidScreenRecordCommands.ACTION_SCREENRECORD_STOP.equals(action)) {
                hudRecording = false;
                overlayMenu.setHudRecording(false);
                Log.i(TAG, "HUD screen record stopped");
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
            public void onActionChosen(OverlayAction action) {
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
        IntentFilter screenRecordFilter = new IntentFilter();
        screenRecordFilter.addAction(RokidScreenRecordCommands.ACTION_SCREENRECORD_START);
        screenRecordFilter.addAction(RokidScreenRecordCommands.ACTION_SCREENRECORD_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(twoFingerReceiver, filter, Context.RECEIVER_EXPORTED);
            registerReceiver(screenRecordStateReceiver, screenRecordFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(twoFingerReceiver, filter);
            registerReceiver(screenRecordStateReceiver, screenRecordFilter);
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
        try {
            unregisterReceiver(screenRecordStateReceiver);
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
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return overlayMenu.isShowing() || isModalInputGuardActive();
        }

        if (overlayMenu.isShowing()) {
            if (action == KeyEvent.ACTION_UP) {
                return true;
            }
            if (event.getRepeatCount() > 0) {
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_NOTIFICATION) {
                restoreCommandVolumeSoon();
                return true;
            }
            return modalNavigation.dispatch(event, new RokidKeyNavigation.Listener() {
                @Override
                public void onNext() {
                    overlayMenu.moveSelection(1);
                    restoreCommandVolumeSoon();
                }

                @Override
                public void onPrevious() {
                    overlayMenu.moveSelection(-1);
                    restoreCommandVolumeSoon();
                }

                @Override
                public void onConfirm() {
                    overlayMenu.confirmNow();
                }

                @Override
                public void onBack() {
                    startModalInputGuard();
                    overlayMenu.hide();
                }
            }) || true;
        }

        if (isOverlayRecForeground()) {
            return false;
        }

        if (isModalInputGuardActive()) {
            return true;
        }

        return false;
    }

    private void onTwoFinger(Direction direction) {
        Log.i(TAG, "two-finger " + direction);
        if (overlayMenu.isShowing()) {
            overlayMenu.moveSelection(direction == Direction.LEFT ? -1 : 1);
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
        startModalInputGuard();
        restoreCommandVolumeSoon();
        overlayMenu.show();
    }

    private void runAction(OverlayAction action) {
        Log.i(TAG, "running action " + action);
        startModalInputGuard();
        overlayMenu.hide();
        restoreCommandVolumeSoon();
        handler.postDelayed(() -> {
            if (action == OverlayAction.SCREENSHOT) {
                RokidArCommands.startArScreenshot(this);
            } else if (action == OverlayAction.RECORD) {
                RokidArCommands.startArRecord(this);
            } else if (hudRecording) {
                RokidScreenRecordCommands.stopHudRecord(this);
            } else {
                RokidScreenRecordCommands.startHudRecord(this);
            }
        }, LAUNCH_DELAY_MS);
    }

    private boolean isModalInputGuardActive() {
        return System.currentTimeMillis() < modalInputGuardUntil;
    }

    private void startModalInputGuard() {
        modalInputGuardUntil = System.currentTimeMillis() + MODAL_INPUT_GUARD_MS;
    }

    private boolean isOverlayRecForeground() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return false;
        }
        CharSequence packageName = root.getPackageName();
        return packageName != null && getPackageName().contentEquals(packageName);
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
