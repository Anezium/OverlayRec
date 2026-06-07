package com.rokid.overlayrec;

import android.view.KeyEvent;

final class RokidKeyNavigation {
    static final long DEFAULT_NAV_DEBOUNCE_MS = 280L;

    private static final int KEY_SWIPE_FORWARD = 183;
    private static final int KEY_SWIPE_BACK = 184;
    private static final int KEY_DOUBLE_TAP = 202;

    private final long navigationDebounceMs;
    private long lastNavigationAt;

    RokidKeyNavigation() {
        this(DEFAULT_NAV_DEBOUNCE_MS);
    }

    RokidKeyNavigation(long navigationDebounceMs) {
        this.navigationDebounceMs = navigationDebounceMs;
    }

    boolean dispatch(KeyEvent event, Listener listener) {
        Command command = commandFor(event.getKeyCode());
        if (command == Command.NONE) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (command == Command.NEXT && shouldAcceptNavigation()) {
                listener.onNext();
            } else if (command == Command.PREVIOUS && shouldAcceptNavigation()) {
                listener.onPrevious();
            } else if (command == Command.CONFIRM) {
                listener.onConfirm();
            } else if (command == Command.BACK) {
                listener.onBack();
            }
        }
        return true;
    }

    static Command commandFor(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KEY_SWIPE_FORWARD) {
            return Command.NEXT;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KEY_SWIPE_BACK) {
            return Command.PREVIOUS;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KEY_DOUBLE_TAP) {
            return Command.CONFIRM;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return Command.BACK;
        }
        return Command.NONE;
    }

    private boolean shouldAcceptNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastNavigationAt < navigationDebounceMs) {
            return false;
        }
        lastNavigationAt = now;
        return true;
    }

    enum Command {
        NEXT,
        PREVIOUS,
        CONFIRM,
        BACK,
        NONE
    }

    interface Listener {
        void onNext();
        void onPrevious();
        void onConfirm();
        void onBack();
    }
}
