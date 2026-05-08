package com.rokid.overlayrec;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 20, 28, 20);
        root.setBackgroundColor(Color.BLACK);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.overlayrec_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(150, 150);
        logoParams.setMargins(0, 0, 0, 8);
        root.addView(logo, logoParams);

        TextView hint = new TextView(this);
        hint.setText("Controls: two finger slide left left right right");
        hint.setTextColor(Color.rgb(218, 226, 234));
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 4, 0, 14);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(53, 215, 166));
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, 0, 0, 12);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button accessibilityButton = button("Enable Accessibility", view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibilityButton);
        root.addView(button("Test AR Screenshot", view ->
                RokidArCommands.startArScreenshot(this)));
        root.addView(button("Test AR Record", view ->
                RokidArCommands.startArRecord(this)));
        root.addView(button("App Details", view -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }));

        setContentView(root);
        accessibilityButton.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusView.setText(isAccessibilityEnabled()
                ? "Service enabled"
                : "Service disabled");
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setTextColor(getColorStateList(R.color.action_button_text));
        button.setBackgroundResource(R.drawable.action_button_bg);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setOnClickListener(listener);
        button.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BUTTON_A)) {
                view.performClick();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                62);
        params.setMargins(0, 6, 0, 6);
        button.setLayoutParams(params);
        return button;
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
}
