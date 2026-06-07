package com.rokid.overlayrec;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

final class OverlayMenu {
    private static final String TAG = "OverlayRecMenu";

    interface Listener {
        void onSelectionChanged();
        void onActionChosen(OverlayAction action);
        void onDismissed();
    }

    private static final long AUTO_CONFIRM_MS = 5000L;
    private static final long TICK_MS = 50L;

    private final Context context;
    private final Listener listener;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MenuView view;

    private OverlayAction selectedAction = OverlayAction.SCREENSHOT;
    private boolean showing = false;
    private boolean hudRecording = false;
    private long shownAt = 0L;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!showing) return;
            long elapsed = System.currentTimeMillis() - shownAt;
            view.setProgress(Math.min(1f, elapsed / (float) AUTO_CONFIRM_MS));
            if (elapsed >= AUTO_CONFIRM_MS) {
                confirmNow();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    OverlayMenu(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.view = new MenuView(context);
        this.view.setSelectedAction(selectedAction);
    }

    boolean isShowing() {
        return showing;
    }

    void show() {
        Log.i(TAG, "show");
        selectedAction = OverlayAction.SCREENSHOT;
        view.setSelectedAction(selectedAction);
        view.setHudRecording(hudRecording);
        view.setProgress(0f);
        shownAt = System.currentTimeMillis();

        if (!showing) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.START;
            windowManager.addView(view, params);
            showing = true;
        }

        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void hide() {
        if (!showing) return;
        Log.i(TAG, "hide");
        handler.removeCallbacks(tick);
        showing = false;
        try {
            windowManager.removeView(view);
        } catch (Exception ignored) {
        }
        listener.onDismissed();
    }

    void select(OverlayAction action) {
        if (!showing || selectedAction == action) return;
        Log.i(TAG, "select " + action);
        selectedAction = action;
        shownAt = System.currentTimeMillis();
        view.setSelectedAction(action);
        view.setProgress(0f);
        listener.onSelectionChanged();
    }

    void moveSelection(int delta) {
        OverlayAction[] actions = OverlayAction.values();
        int index = 0;
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] == selectedAction) {
                index = i;
                break;
            }
        }
        int next = (index + delta) % actions.length;
        if (next < 0) {
            next += actions.length;
        }
        select(actions[next]);
    }

    void setHudRecording(boolean recording) {
        hudRecording = recording;
        view.setHudRecording(recording);
    }

    void confirmNow() {
        if (!showing) return;
        Log.i(TAG, "confirm " + selectedAction);
        OverlayAction action = selectedAction;
        listener.onActionChosen(action);
    }

    private static final class MenuView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private OverlayAction selectedAction = OverlayAction.SCREENSHOT;
        private boolean hudRecording = false;
        private float progress = 0f;

        MenuView(Context context) {
            super(context);
        }

        void setSelectedAction(OverlayAction action) {
            selectedAction = action;
            invalidate();
        }

        void setHudRecording(boolean recording) {
            hudRecording = recording;
            invalidate();
        }

        void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float density = getResources().getDisplayMetrics().density;
            float panelW = Math.min(w - 32f * density, 390f * density);
            float panelH = Math.min(h - 32f * density, 292f * density);
            float left = (w - panelW) / 2f;
            float top = (h - panelH) / 2f;

            paint.setColor(Color.argb(232, 0, 0, 0));
            rect.set(left, top, left + panelW, top + panelH);
            canvas.drawRoundRect(rect, 18f * density, 18f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(Color.rgb(86, 96, 108));
            canvas.drawRoundRect(rect, 18f * density, 18f * density, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            paint.setTextSize(22f * density);
            paint.setFakeBoldText(true);
            canvas.drawText("OverlayRec", w / 2f, top + 44f * density, paint);

            float optionTop = top + 70f * density;
            float optionH = 48f * density;
            float gap = 8f * density;
            float optionW = panelW - 30f * density;
            drawOption(canvas, left + 15f * density, optionTop, optionW, optionH,
                    OverlayAction.SCREENSHOT.label(hudRecording),
                    selectedAction == OverlayAction.SCREENSHOT,
                    density);
            drawOption(canvas, left + 15f * density, optionTop + optionH + gap, optionW, optionH,
                    OverlayAction.RECORD.label(hudRecording),
                    selectedAction == OverlayAction.RECORD,
                    density);
            drawOption(canvas, left + 15f * density, optionTop + (optionH + gap) * 2f,
                    optionW,
                    optionH,
                    OverlayAction.HUD_RECORD.label(hudRecording),
                    selectedAction == OverlayAction.HUD_RECORD,
                    density);

            paint.setFakeBoldText(false);
            paint.setTextSize(13f * density);
            paint.setColor(Color.rgb(188, 198, 208));
            canvas.drawText("Swipe selects, OK confirms in 5s", w / 2f,
                    top + panelH - 22f * density, paint);

            paint.setColor(Color.rgb(53, 215, 166));
            rect.set(left, top + panelH - 6f * density,
                    left + panelW * progress, top + panelH);
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint);
        }

        private void drawOption(Canvas canvas, float left, float top, float width, float height,
                                String label, boolean selected, float density) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(selected ? Color.rgb(53, 215, 166) : Color.argb(255, 42, 50, 58));
            rect.set(left, top, left + width, top + height);
            canvas.drawRoundRect(rect, 12f * density, 12f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(selected ? 4f * density : 2f * density);
            paint.setColor(selected ? Color.WHITE : Color.rgb(96, 108, 120));
            canvas.drawRoundRect(rect, 12f * density, 12f * density, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(selected ? Color.rgb(9, 22, 18) : Color.WHITE);
            paint.setTextSize(18f * density);
            paint.setFakeBoldText(selected);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, left + width / 2f, top + height / 2f + 7f * density, paint);
            paint.setFakeBoldText(false);
        }
    }
}
