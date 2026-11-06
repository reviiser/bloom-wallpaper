package com.reviiser.bloom;

import android.service.dreams.DreamService;

/** The screensaver: the forest and nothing else. Any button on the remote wakes the TV, as a screensaver should. */
public class BloomDream extends DreamService {
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        setContentView(new BloomView(this, false));
    }
}
