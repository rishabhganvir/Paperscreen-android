package com.paperscreen;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;

public class OverlayService extends Service {

    public static final String CMD_SET_ALPHA = "com.paperscreen.SET_ALPHA";
    public static final String EXTRA_ALPHA   = "alpha";

    public static boolean running = false;
    public static int     alpha   = 45; // 0-255

    private WindowManager   wm;
    private View            overlayView;
    private Paint           overlayPaint;
    private Bitmap          textureBitmap;

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Build the Kindle paper grain texture once
        textureBitmap = buildTexture();

        // Transparent view that just draws the texture bitmap
        overlayView = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (textureBitmap != null && !textureBitmap.isRecycled()) {
                    canvas.drawBitmap(textureBitmap, 0, 0, overlayPaint);
                }
            }
        };

        overlayPaint = new Paint();
        overlayPaint.setAlpha(alpha);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_FOCUSABLE + NOT_TOUCHABLE = clicks pass through
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        wm.addView(overlayView, params);
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CMD_SET_ALPHA.equals(intent.getAction())) {
            alpha = intent.getIntExtra(EXTRA_ALPHA, alpha);
            alpha = Math.max(5, Math.min(225, alpha));
            if (overlayPaint != null) {
                overlayPaint.setAlpha(alpha);
                if (overlayView != null) overlayView.invalidate();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && wm != null) {
            wm.removeView(overlayView);
        }
        if (textureBitmap != null) {
            textureBitmap.recycle();
        }
        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // -----------------------------------------------------------------------
    // Kindle paper grain texture — same 3-layer sin noise as Windows version
    // -----------------------------------------------------------------------
    private Bitmap buildTexture() {
        // Use a smaller tile and repeat it — way faster, looks identical
        // 512x512 tile tiled across screen
        int tw = 512, th = 512;
        int[] pixels = new int[tw * th];

        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                double fx = x, fy = y;
                double g = noise(fx, fy, 1.0) * 0.55
                         + noise(fx * 0.28, fy * 0.28, 2.8) * 0.30
                         + noise(fx * 0.06, fy * 0.06, 6.0) * 0.15;

                // Kindle warm tint: R=100% G=97% B=86%
                int r = (int) Math.min(255, g * 255 * 1.00);
                int g2= (int) Math.min(255, g * 255 * 0.97);
                int b = (int) Math.min(255, g * 255 * 0.86);
                pixels[y * tw + x] = 0xFF000000 | (r << 16) | (g2 << 8) | b;
            }
        }

        // Create tiled bitmap at screen size
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels, sh = dm.heightPixels;

        Bitmap tile   = Bitmap.createBitmap(pixels, tw, th, Bitmap.Config.ARGB_8888);
        Bitmap screen = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c      = new Canvas(screen);
        Paint  p      = new Paint();

        for (int ty = 0; ty < sh; ty += th) {
            for (int tx = 0; tx < sw; tx += tw) {
                c.drawBitmap(tile, tx, ty, p);
            }
        }
        tile.recycle();
        return screen;
    }

    private static double noise(double x, double y, double freq) {
        double v = Math.sin(x * 127.1 * freq + y * 311.7) * 43758.5453
                 + Math.sin(x * 269.5 * freq + y * 183.3 * freq) * 43758.5453
                 + Math.sin((x + y) * 74.9 * freq) * 43758.5453;
        v -= Math.floor(v);
        if (v < 0) v = -v;
        return v;
    }
}
