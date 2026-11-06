package com.reviiser.bloom;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.reviiser.bloom.core.Engine;
import com.reviiser.bloom.core.Menu;

/** Opened from the home screen: the forest full screen, with the whole menu a press of Select away. */
public class BloomActivity extends Activity {
    private Engine forest;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        forest = Forest.engine(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(new BloomView(this, true));
        hideBars();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** The remote's buttons, in the menu's terms. Different remotes have different buttons, so several do each job. */
    private static int menuKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return Menu.KEY_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Menu.KEY_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Menu.KEY_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Menu.KEY_RIGHT;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return Menu.KEY_SELECT;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_BUTTON_B:
                return Menu.KEY_BACK;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_UP:
                return Menu.KEY_PREV_TAB;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_TAB:
                return Menu.KEY_NEXT_TAB;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return Menu.KEY_DIGIT + code - KeyEvent.KEYCODE_0;
                return 0;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = menuKey(event.getKeyCode());
        if (key == 0) return super.dispatchKeyEvent(event); // volume and home belong to the TV
        boolean open = forest.menuOpen();
        if (!open && key == Menu.KEY_BACK && event.getKeyCode() != KeyEvent.KEYCODE_MENU) return super.dispatchKeyEvent(event); // Back from the forest leaves the app
        if (event.getAction() == KeyEvent.ACTION_DOWN) forest.key(key);
        return true;
    }
}
