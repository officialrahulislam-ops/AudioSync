package com.audiosync.receiver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements AudioReceiver.StatusListener {
    private static final String TAG = "AudioSyncReceiver";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView statusText;
    private EditText ipEditText;
    private Button connectButton;
    private Button stopButton;

    private AudioReceiver audioReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        ipEditText = findViewById(R.id.ipEditText);
        connectButton = findViewById(R.id.connectButton);
        stopButton = findViewById(R.id.stopButton);

        checkPermissions();

        connectButton.setOnClickListener(v -> {
            String ip = ipEditText.getText().toString().trim();
            if (!ip.isEmpty()) {
                connectToSender(ip);
            } else {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show();
            }
        });

        stopButton.setOnClickListener(v -> stopStreaming());

        audioReceiver = new AudioReceiver(this);
        startDiscoveryListener();
    }

    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        };

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private void startDiscoveryListener() {
        // Discovery is now handled by the manual connect button
        // The receiver just listens on port 19827 for incoming audio
        runOnUiThread(() -> statusText.setText("Ready. Enter sender IP and press Connect."));
    }

    private void connectToSender(String ip) {
        // The sender will connect to us on port 19827
        // We just start listening
        runOnUiThread(() -> {
            statusText.setText("Listening for audio on port 19827...");
            connectButton.setEnabled(false);
            stopButton.setEnabled(true);
        });
        audioReceiver.start();
    }

    private void stopStreaming() {
        if (audioReceiver != null) {
            audioReceiver.stop();
        }
        runOnUiThread(() -> {
            statusText.setText("Stopped");
            connectButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    @Override
    public void onStatusChanged(String status) {
        runOnUiThread(() -> statusText.setText(status));
    }

    @Override
    public void onStatsUpdated(int packetsReceived, int framesPlayed, int bufferMs) {
        runOnUiThread(() -> {
            String stats = String.format("Packets: %d | Frames: %d | Buffer: %dms",
                    packetsReceived, framesPlayed, bufferMs);
            // Update a stats text view if we add one, or append to status
            statusText.setText(statusText.getText() + "\n" + stats);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}