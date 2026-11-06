package com.reviiser.bloom;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.reviiser.bloom.core.Engine;
import com.reviiser.bloom.core.Menu;

/** The one forest in this process, shared by the screensaver and the app. Only ever one screen drives it at a time. */
final class Forest {
    static final String TAG = "Bloom";
    private static Engine engine;
    private static Runner current;

    private Forest() {}

    private static final class Device implements Menu.Host {
        final Context app;
        Device(Context context) { app = context.getApplicationContext(); }

        @Override public boolean isScreensaver() {
            try {
                String chosen = Settings.Secure.getString(app.getContentResolver(), "screensaver_components");
                return chosen != null && chosen.contains(app.getPackageName());
            } catch (RuntimeException e) {
                return false;
            }
        }

        boolean fireTv() { return app.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv"); }

        @Override public void openScreensaverSettings() {
            try {
                app.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.i(TAG, "this TV has no screensaver settings to open: " + e);
            }
        }

        @Override public String screensaverHint() {
            if (isScreensaver()) return "Bloom is your screensaver. SELECT for TV settings.";
            return fireTv() ? "Fire TV needs a one-time setup: see the instructions." : "SELECT, then choose Bloom in the TV's settings.";
        }
    }

    static synchronized Engine engine(Context context) {
        if (engine == null) {
            Context app = context.getApplicationContext();
            WindowManager windows = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            windows.getDefaultDisplay().getRealMetrics(metrics);
            int w = Math.max(metrics.widthPixels, metrics.heightPixels), h = Math.min(metrics.widthPixels, metrics.heightPixels);
            engine = new Engine(app.getFilesDir(), new Device(app), w, h);
        }
        return engine;
    }

    /** Runs the heartbeat on its own thread for as long as its screen is showing. A newer screen takes over from an older one. */
    static final class Runner extends Thread {
        private final BloomView view;
        private final Engine forest;
        private final Context context;
        private volatile boolean running = true;

        Runner(Context context, BloomView view, Engine forest) {
            super("bloom");
            this.context = context.getApplicationContext();
            this.view = view;
            this.forest = forest;
            synchronized (Forest.class) {
                if (current != null) current.finish();
                current = this;
            }
        }

        void finish() { running = false; }

        private void checkBattery() {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int plugged = battery == null ? 1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 1);
            boolean present = battery != null && battery.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);
            forest.onBattery = present && plugged == 0;
        }

        @Override public void run() {
            // A little below normal, but not so low that the system lumps it in with background work and its timers go slack.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT + 5);
            long worked = 0, windowStart = System.nanoTime(), count = 0, nextBattery = 0;
            synchronized (forest) {
                try {
                    if (forest.world == null) forest.start();
                    else forest.resume();
                } catch (RuntimeException e) {
                    Log.e(TAG, "the forest would not start", e);
                    return;
                }
            }
            view.postInvalidate();
            long nextFrame = System.nanoTime();
            while (running) {
                long t0 = System.nanoTime();
                synchronized (forest) {
                    if (t0 > nextBattery) {
                        checkBattery();
                        nextBattery = t0 + 60_000_000_000L;
                    }
                    try {
                        forest.frame();
                    } catch (RuntimeException e) {
                        // A screensaver carries on: one bad moment in the forest shouldn't take down the TV's screen.
                        Log.e(TAG, "a frame went wrong", e);
                    }
                    view.take(forest);
                }
                long spent = System.nanoTime() - t0;
                worked += spent;
                if (++count >= 240) {
                    long window = System.nanoTime() - windowStart;
                    Log.i(TAG, String.format(java.util.Locale.US, "%.1f fps, %.1f ms a frame of work, %.0f%% of one core",
                            count * 1e9 / window, worked / 1e6 / count, worked * 100.0 / window));
                    count = 0;
                    worked = 0;
                    windowStart = System.nanoTime();
                }
                // Kept to the clock, so that the odd slow frame doesn't slow the forest down.
                long period = 1000000000L / Math.max(1, forest.fps());
                nextFrame = Math.max(nextFrame + period, System.nanoTime() - period);
                long left = (nextFrame - System.nanoTime()) / 1000000;
                if (left > 0) {
                    try {
                        Thread.sleep(left);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            synchronized (forest) {
                forest.stop();
            }
        }
    }
}
