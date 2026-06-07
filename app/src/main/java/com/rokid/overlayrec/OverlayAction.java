package com.rokid.overlayrec;

enum OverlayAction {
    SCREENSHOT,
    RECORD,
    HUD_RECORD;

    String label(boolean hudRecording) {
        if (this == SCREENSHOT) {
            return "AR Screenshot";
        }
        if (this == RECORD) {
            return "AR Record";
        }
        return hudRecording ? "Stop HUD Record" : "HUD Record";
    }
}
