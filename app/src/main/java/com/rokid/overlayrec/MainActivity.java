package com.rokid.overlayrec;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

public class MainActivity extends Activity {
    private static final long SERVICE_STATUS_REFRESH_MS = 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RokidKeyNavigation keyNavigation = new RokidKeyNavigation();
    private final FocusListState focus = new FocusListState();

    private MainMenuView menuView;
    private boolean serviceEnabled;
    private boolean hudRecording;

    private final BroadcastReceiver screenRecordStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (RokidScreenRecordCommands.ACTION_SCREENRECORD_START.equals(action)) {
                hudRecording = true;
                invalidateMenu();
            } else if (RokidScreenRecordCommands.ACTION_SCREENRECORD_STOP.equals(action)) {
                hudRecording = false;
                invalidateMenu();
            }
        }
    };

    private final Runnable serviceStatusRefresh = new Runnable() {
        @Override
        public void run() {
            refreshServiceState();
            handler.postDelayed(this, SERVICE_STATUS_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        focus.setItemCount(MainMenuAction.values().length);
        menuView = new MainMenuView();
        setContentView(menuView);
        menuView.requestFocus();
        registerScreenRecordReceiver();
        refreshServiceState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (menuView != null) {
            menuView.requestFocus();
        }
        refreshServiceState();
        handler.removeCallbacks(serviceStatusRefresh);
        handler.postDelayed(serviceStatusRefresh, SERVICE_STATUS_REFRESH_MS);
        invalidateMenu();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(serviceStatusRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(serviceStatusRefresh);
        try {
            unregisterReceiver(screenRecordStateReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
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

    private void registerScreenRecordReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(RokidScreenRecordCommands.ACTION_SCREENRECORD_START);
        filter.addAction(RokidScreenRecordCommands.ACTION_SCREENRECORD_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenRecordStateReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(screenRecordStateReceiver, filter);
        }
    }

    private void moveFocus(int delta) {
        focus.setItemCount(MainMenuAction.values().length);
        focus.move(delta);
        ensureFocusVisible();
        invalidateMenu();
    }

    private void activateFocusedItem() {
        MainMenuAction action = MainMenuAction.values()[focus.selectedIndex()];
        if (action == MainMenuAction.HUD_VIDEOS) {
            startActivity(new Intent(this, HudVideoActivity.class));
        } else if (action == MainMenuAction.HUD_RECORD) {
            toggleHudRecord();
        } else if (action == MainMenuAction.AR_SCREENSHOT) {
            RokidArCommands.startArScreenshot(this);
        } else if (action == MainMenuAction.AR_RECORD) {
            RokidArCommands.startArRecord(this);
        } else if (action == MainMenuAction.ACCESSIBILITY) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else if (action == MainMenuAction.APP_DETAILS) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void toggleHudRecord() {
        if (hudRecording) {
            RokidScreenRecordCommands.stopHudRecord(this);
            hudRecording = false;
        } else {
            RokidScreenRecordCommands.startHudRecord(this);
            hudRecording = true;
        }
        invalidateMenu();
    }

    private void ensureFocusVisible() {
        if (menuView != null) {
            focus.ensureSelectedVisible(menuView.visibleRows());
        }
    }

    private void refreshServiceState() {
        boolean enabled = isAccessibilityEnabled();
        if (serviceEnabled != enabled) {
            serviceEnabled = enabled;
            invalidateMenu();
        }
    }

    private void invalidateMenu() {
        if (menuView != null) {
            menuView.invalidate();
        }
    }

    private String actionLabel(MainMenuAction action) {
        if (action == MainMenuAction.HUD_RECORD) {
            return hudRecording ? "Stop HUD recording" : action.label;
        }
        return action.label;
    }

    private String actionHelper(MainMenuAction action) {
        if (action == MainMenuAction.HUD_RECORD) {
            return hudRecording ? "Stop screen-only capture" : action.helper;
        }
        if (action == MainMenuAction.ACCESSIBILITY) {
            return serviceEnabled ? "Gesture popup is available" : "Enable this for the gesture popup";
        }
        return action.helper;
    }

    private boolean isAccessibilityEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        String expected = new ComponentName(this, OverlayRecService.class).flattenToString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (expected.equalsIgnoreCase(splitter.next())) return true;
        }
        return false;
    }

    private enum MainMenuAction {
        HUD_VIDEOS("HUD videos", "Browse saved clips, send, delete"),
        HUD_RECORD("HUD recording", "Record the glasses display only"),
        AR_SCREENSHOT("AR screenshot", "Take one Rokid AR camera photo"),
        AR_RECORD("AR recording", "Start Rokid AR camera video"),
        ACCESSIBILITY("Accessibility service", "Enable this for the gesture popup"),
        APP_DETAILS("App settings", "Permissions, storage, app info");

        final String label;
        final String helper;

        MainMenuAction(String label, String helper) {
            this.label = label;
            this.helper = helper;
        }
    }

    private final class MainMenuView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        MainMenuView() {
            super(MainActivity.this);
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
            int side = dp(18);
            drawHeader(canvas, width, side);
            drawMenu(canvas, side, dp(104), width - side, getHeight() - dp(14));
        }

        int visibleRows() {
            int available = Math.max(0, getHeight() - dp(104) - dp(14));
            return Math.max(1, available / rowStride());
        }

        private void drawHeader(Canvas canvas, int width, int side) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(25));
            paint.setColor(Color.rgb(53, 215, 166));
            canvas.drawText("OverlayRec", side, dp(36), paint);

            paint.setTextSize(sp(12));
            paint.setColor(serviceEnabled ? Color.rgb(53, 215, 166) : Color.rgb(224, 128, 110));
            canvas.drawText(serviceEnabled ? "Accessibility service: ON" : "Accessibility service: OFF",
                    side,
                    dp(62),
                    paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setTextSize(sp(17));
            paint.setColor(Color.rgb(218, 226, 234));
            canvas.drawText((focus.selectedIndex() + 1) + "/" + MainMenuAction.values().length,
                    width - side,
                    dp(62),
                    paint);

            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(11));
            paint.setColor(Color.rgb(154, 170, 184));
            canvas.drawText("Double-finger menu: L L R R", side, dp(82), paint);
        }

        private void drawMenu(Canvas canvas, int left, int top, int right, int bottom) {
            focus.ensureSelectedVisible(visibleRows());
            MainMenuAction[] actions = MainMenuAction.values();
            int end = Math.min(actions.length, focus.firstVisibleIndex() + visibleRows());
            for (int index = focus.firstVisibleIndex(); index < end; index++) {
                int rowTop = top + (index - focus.firstVisibleIndex()) * rowStride();
                drawRow(canvas, actions[index], index, left, rowTop, right);
            }

            if (actions.length > visibleRows()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTextSize(sp(10));
                paint.setColor(Color.rgb(126, 144, 158));
                canvas.drawText((focus.firstVisibleIndex() + 1) + "-" + end + "/" + actions.length,
                        right,
                        bottom - dp(2),
                        paint);
            }
        }

        private void drawRow(Canvas canvas, MainMenuAction action, int index, int left, int top, int right) {
            boolean focused = focus.selectedIndex() == index;
            int bottom = top + rowHeight();

            strokePaint.setStrokeWidth(focused ? dp(3) : dp(1));
            strokePaint.setColor(focused ? Color.rgb(53, 215, 166) : Color.rgb(72, 88, 102));
            rect.set(left, top + dp(4), right, bottom);
            canvas.drawRoundRect(rect, dp(6), dp(6), strokePaint);

            if (focused) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(53, 215, 166));
                canvas.drawRect(left, top + dp(18), left + dp(4), bottom - dp(18), paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(17));
            paint.setColor(focused ? Color.rgb(246, 250, 252) : Color.rgb(230, 236, 242));
            int textLeft = left + dp(16);
            int textRight = right - dp(16);
            canvas.drawText(ellipsize(actionLabel(action), textRight - textLeft), textLeft, top + dp(29), paint);

            paint.setTextSize(sp(11));
            paint.setColor(Color.rgb(158, 176, 190));
            canvas.drawText(ellipsize(actionHelper(action), textRight - textLeft), textLeft, top + dp(50), paint);
        }

        private int rowStride() {
            return dp(70);
        }

        private int rowHeight() {
            return dp(62);
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
