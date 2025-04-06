package com.github.gcacace.signaturepad.view;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewTreeObserver;

public class ViewTreeObserverCompat {

    private ViewTreeObserverCompat() {
        // empty constructor
    }

    /**
     * Remove a previously installed global layout callback.
     * @param observer the view observer
     * @param victim the victim
     */
    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public static void removeOnGlobalLayoutListener(ViewTreeObserver observer, ViewTreeObserver.OnGlobalLayoutListener victim) {
        // Future (API16+)...
        if (Build.VERSION.SDK_INT >= 16) {
            observer.removeOnGlobalLayoutListener(victim);
        }
        // Legacy...
        else {
            observer.removeGlobalOnLayoutListener(victim);
        }
    }
}
