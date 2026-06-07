package com.rokid.overlayrec;

final class FocusListState {
    private int itemCount;
    private int selectedIndex;
    private int firstVisibleIndex;

    void setItemCount(int itemCount) {
        this.itemCount = Math.max(0, itemCount);
        if (this.itemCount == 0) {
            selectedIndex = 0;
            firstVisibleIndex = 0;
            return;
        }
        if (selectedIndex >= this.itemCount) {
            selectedIndex = this.itemCount - 1;
        }
        if (firstVisibleIndex >= this.itemCount) {
            firstVisibleIndex = Math.max(0, this.itemCount - 1);
        }
    }

    void move(int delta) {
        if (itemCount <= 0) {
            return;
        }
        selectedIndex = (selectedIndex + delta + itemCount) % itemCount;
    }

    void ensureSelectedVisible(int visibleRows) {
        if (itemCount <= 0) {
            firstVisibleIndex = 0;
            return;
        }

        int safeRows = Math.max(1, visibleRows);
        if (selectedIndex < firstVisibleIndex) {
            firstVisibleIndex = selectedIndex;
        } else if (selectedIndex >= firstVisibleIndex + safeRows) {
            firstVisibleIndex = selectedIndex - safeRows + 1;
        }

        int maxFirst = Math.max(0, itemCount - safeRows);
        if (firstVisibleIndex > maxFirst) {
            firstVisibleIndex = maxFirst;
        }
    }

    int selectedIndex() {
        return selectedIndex;
    }

    int firstVisibleIndex() {
        return firstVisibleIndex;
    }
}
