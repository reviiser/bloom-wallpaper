package com.reviiser.bloom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.reviiser.bloom.core.Crt;
import com.reviiser.bloom.core.Engine;
import com.reviiser.bloom.core.Menu;

/** Shows the forest, scaled up to fill the screen, with the old tube and the menu over it when they are on. */
final class BloomView extends View {
    private final Engine forest;
    private Forest.Runner runner;
    private final Object lock = new Object();
    private int[] frontPicture, frontMenu;
    private int pictureW, pictureH, barY;
    private double crt;
    private int crtScale;
    private String status = "";
    private boolean menuShowing, hint;
    private long hintUntil;

    private Bitmap pictureBitmap, menuBitmap, maskBitmap;
    private double maskStrength = -1, wantedStrength = -1;
    private int settled;
    private int maskScale, maskW, maskH;
    private final Paint sharp = new Paint(), smooth = new Paint(Paint.FILTER_BITMAP_FLAG), multiply = new Paint(), add = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect(), dst = new Rect();

    BloomView(Context context, boolean showHint) {
        super(context);
        forest = Forest.engine(context);
        setBackgroundColor(Color.BLACK);
        setKeepScreenOn(true);
        hint = showHint;
        sharp.setFilterBitmap(false);
        multiply.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        text.setColor(0xffa8f070);
        text.setTypeface(Typeface.MONOSPACE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        hintUntil = System.currentTimeMillis() + 7000;
        runner = new Forest.Runner(getContext(), this, forest);
        runner.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (runner != null) runner.finish();
        runner = null;
        super.onDetachedFromWindow();
    }

    /** From the heartbeat's thread: take a copy of whatever has changed, for the next draw. */
    void take(Engine e) {
        if (!e.takePictureChanged()) return;
        prepareMask(e);
        int[] picture = e.picture(), menu = e.menuPixels();
        synchronized (lock) {
            if (frontPicture == null || frontPicture.length != picture.length) frontPicture = new int[picture.length];
            System.arraycopy(picture, 0, frontPicture, 0, picture.length);
            pictureW = e.pictureW();
            pictureH = e.pictureH();
            barY = e.barY();
            crt = e.cfg.crt;
            crtScale = e.cfg.pixelSize;
            menuShowing = menu != null;
            if (menu != null) {
                if (frontMenu == null) frontMenu = new int[menu.length];
                System.arraycopy(menu, 0, frontMenu, 0, menu.length);
            }
        }
        postInvalidate();
    }

    /** The tube's mask takes real work, so it's only made once the slider has come to rest, and never on the screen's own thread. */
    private void prepareMask(Engine e) {
        int w = getWidth(), h = getHeight();
        double strength = e.cfg.crt;
        int scale = e.cfg.pixelSize;
        if (strength <= 0.001 || w == 0) return;
        if (strength == maskStrength && scale == maskScale && w == maskW && h == maskH) return;
        if (strength != wantedStrength) {
            wantedStrength = strength;
            settled = 0;
            return;
        }
        if (++settled < 4 && maskBitmap != null) return;
        Bitmap made = Bitmap.createBitmap(Crt.mask(w, h, strength, scale), w, h, Bitmap.Config.ARGB_8888);
        synchronized (lock) {
            maskBitmap = made;
        }
        maskStrength = strength;
        maskScale = scale;
        maskW = w;
        maskH = h;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        boolean withMenu;
        double strength;
        int bar, scale;
        synchronized (lock) {
            withMenu = menuShowing;
            strength = crt;
            bar = barY;
            scale = crtScale;
            if (frontPicture != null) {
                if (pictureBitmap == null || pictureBitmap.getWidth() != pictureW || pictureBitmap.getHeight() != pictureH)
                    pictureBitmap = Bitmap.createBitmap(pictureW, pictureH, Bitmap.Config.ARGB_8888);
                pictureBitmap.setPixels(frontPicture, 0, pictureW, 0, 0, pictureW, pictureH);
            }
            if (withMenu && frontMenu != null) {
                if (menuBitmap == null) menuBitmap = Bitmap.createBitmap(Menu.W, Menu.H, Bitmap.Config.ARGB_8888);
                menuBitmap.setPixels(frontMenu, 0, Menu.W, 0, 0, Menu.W, Menu.H);
            }
        }
        if (pictureBitmap == null) {
            drawStatus(canvas, w, h, forest.status.isEmpty() ? "A forest is growing" : forest.status);
            return;
        }
        src.set(0, 0, pictureBitmap.getWidth(), pictureBitmap.getHeight());
        // The picture is cropped to the screen exactly as the art was sized to it: whole art pixels, the rest off the edge.
        int px = Math.max(1, forest.cfg.pixelSize);
        dst.set(0, 0, pictureBitmap.getWidth() * px, pictureBitmap.getHeight() * px);
        if (strength > 0.001) {
            canvas.drawBitmap(pictureBitmap, src, dst, smooth);
            Bitmap mask;
            synchronized (lock) {
                mask = maskBitmap;
            }
            if (mask != null) canvas.drawBitmap(mask, 0, 0, multiply);
            // The hum bar: a band only a little brighter than the rest, rolling down the screen.
            int level = (int) (255 * 0.035 * strength);
            add.setShader(new LinearGradient(0, bar, 0, bar + Crt.BAR_HEIGHT, new int[]{0, Color.argb(level, 255, 255, 255), 0}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, bar, w, bar + Crt.BAR_HEIGHT, add);
        } else {
            canvas.drawBitmap(pictureBitmap, src, dst, sharp);
        }
        if (withMenu && menuBitmap != null) {
            int menuScale = Math.max(1, Math.min(w / Menu.W, h / Menu.H));
            int x = (w - Menu.W * menuScale) / 2, y = (h - Menu.H * menuScale) / 2;
            src.set(0, 0, Menu.W, Menu.H);
            dst.set(x, y, x + Menu.W * menuScale, y + Menu.H * menuScale);
            canvas.drawBitmap(menuBitmap, src, dst, sharp);
        } else if (hint && System.currentTimeMillis() < hintUntil) {
            text.setTextSize(h / 45f);
            canvas.drawText("PRESS SELECT FOR THE MENU", h / 30f, h - h / 30f, text);
            postInvalidateDelayed(1000);
        }
        String busy = forest.status;
        if (!busy.isEmpty()) drawStatus(canvas, w, h, busy);
    }

    private void drawStatus(Canvas canvas, int w, int h, String message) {
        text.setTextSize(h / 30f);
        canvas.drawText(message + "...", (w - text.measureText(message + "...")) / 2, h / 2f, text);
        postInvalidateDelayed(500);
    }
}
