package com.paperscreen;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnToggle  = findViewById(R.id.btn_toggle);
        Button btnLighter = findViewById(R.id.btn_lighter);
        Button btnDarker  = findViewById(R.id.btn_darker);
        SeekBar seekBar   = findViewById(R.id.seekbar_opacity);
        TextView tvStatus = findViewById(R.id.tv_status);

        seekBar.setMax(220);
        seekBar.setProgress(OverlayService.alpha);

        updateStatus(tvStatus, seekBar.getProgress());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) {
                    int alpha = Math.max(5, progress);
                    sendCommand(OverlayService.CMD_SET_ALPHA, alpha);
                    updateStatus(tvStatus, alpha);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnToggle.setOnClickListener(v -> {
            if (!checkPermission()) return;
            if (OverlayService.running) {
                stopService(new Intent(this, OverlayService.class));
                btnToggle.setText("Turn On");
                tvStatus.setText("Off");
            } else {
                startOverlayService();
                btnToggle.setText("Turn Off");
                updateStatus(tvStatus, seekBar.getProgress());
            }
        });

        btnLighter.setOnClickListener(v -> {
            if (!checkPermission()) return;
            int a = Math.max(5, OverlayService.alpha - 20);
            sendCommand(OverlayService.CMD_SET_ALPHA, a);
            seekBar.setProgress(a);
            updateStatus(tvStatus, a);
        });

        btnDarker.setOnClickListener(v -> {
            if (!checkPermission()) return;
            int a = Math.min(225, OverlayService.alpha + 20);
            sendCommand(OverlayService.CMD_SET_ALPHA, a);
            seekBar.setProgress(a);
            updateStatus(tvStatus, a);
        });

        // Update toggle button label based on current state
        btnToggle.setText(OverlayService.running ? "Turn Off" : "Turn On");
        if (!OverlayService.running) tvStatus.setText("Off");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-start overlay when app opens if permission already granted
        if (Settings.canDrawOverlays(this) && !OverlayService.running) {
            startOverlayService();
            Button btn = findViewById(R.id.btn_toggle);
            if (btn != null) btn.setText("Turn Off");
        }
    }

    private void startOverlayService() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra(OverlayService.EXTRA_ALPHA, OverlayService.alpha);
        startService(intent);
    }

    private void sendCommand(String cmd, int value) {
        if (!OverlayService.running) {
            startOverlayService();
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(cmd);
        intent.putExtra(OverlayService.EXTRA_ALPHA, value);
        startService(intent);
    }

    private boolean checkPermission() {
        if (Settings.canDrawOverlays(this)) return true;
        Toast.makeText(this, "Grant 'Display over other apps' permission first", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY);
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OVERLAY && Settings.canDrawOverlays(this)) {
            startOverlayService();
        }
    }

    private void updateStatus(TextView tv, int alpha) {
        int pct = alpha * 100 / 225;
        tv.setText("Opacity: " + pct + "%");
    }
}
