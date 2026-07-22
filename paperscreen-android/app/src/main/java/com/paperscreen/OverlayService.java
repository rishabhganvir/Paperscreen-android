package com.paperscreen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
    private static final String CHANNEL_ID   = "paperscreen_channel";

    public static boolean running = false;
    public static int     alpha   = 45;

    private WindowManager wm;
    private View          overlayView;
    private Paint         overlayPaint;
    private Bitmap        textureBitmap;

    @Override
    public void onCreate() {
        super.onCreate();

        // Foreground service notification — Android cannot kill this
        createNotificationChannel();
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_IMMUTABLE);
        Notification notif = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("PaperScreen")
                .setContentText("Paper texture active — tap to adjust")
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(1, notif);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        textureBitmap = buildTexture();

        overlayPaint = new Paint();
        overlayPaint.setAlpha(alpha);

        overlayView = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (textureBitmap != null && !textureBitmap.isRecycled()) {
                    canvas.drawBitmap(textureBitmap, 0, 0, overlayPaint);
                }
            }
        };

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        // START_STICKY — if Android kills it, restart automatically
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && wm != null) {
            try { wm.removeView(overlayView); } catch (Exception e) {}
        }
        if (textureBitmap != null) textureBitmap.recycle();
        running = false;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "PaperScreen", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps paper texture active");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Bitmap buildTexture() {
        int tw = 512, th = 512;
        int[] pixels = new int[tw * th];
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                double fx = x, fy = y;
                double g = noise(fx, fy, 1.0) * 0.55
                         + noise(fx * 0.28, fy * 0.28, 2.8) * 0.30
                         + noise(fx * 0.06, fy * 0.06, 6.0) * 0.15;
                int r  = (int) Math.min(255, g * 255 * 1.00);
                int gv = (int) Math.min(255, g * 255 * 0.97);
                int b  = (int) Math.min(255, g * 255 * 0.86);
                pixels[y * tw + x] = 0xFF000000 | (r << 16) | (gv << 8) | b;
            }
        }
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = dm.widthPixels, sh = dm.heightPixels;
        Bitmap tile   = Bitmap.createBitmap(pixels, tw, th, Bitmap.Config.ARGB_8888);
        Bitmap screen = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(screen);
        Paint  p = new Paint();
        for (int ty = 0; ty < sh; ty += th)
            for (int tx = 0; tx < sw; tx += tw)
                c.drawBitmap(tile, tx, ty, p);
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
