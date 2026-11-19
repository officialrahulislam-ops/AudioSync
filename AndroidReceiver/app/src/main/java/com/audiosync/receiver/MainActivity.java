package com.audiosync.receiver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AudioSyncReceiver";
    private static final int DISCOVERY_PORT = 19826;
    private static final int STREAM_PORT = 19827;

    private TextView statusText;
    private EditText ipEditText;
    private Button connectButton;
    private Button stopButton;

    private DatagramSocket discoverySocket;
    private DatagramSocket streamSocket;
    private boolean isStreaming = false;

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
        startDiscoveryListener();
    }

    private void checkPermissions() {
        ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result.getOrDefault(Manifest.permission.INTERNET, false) &&
                    result.getOrDefault(Manifest.permission.ACCESS_WIFI_STATE, false)) {
                    Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Some permissions denied", Toast.LENGTH_SHORT).show();
                }
            });

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) !=
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) !=
            PackageManager.PERMISSION_GRANTED) {

            permissionLauncher.launch(new String[]{
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_WIFI_STATE
            });
        }
    }

    private void startDiscoveryListener() {
        new Thread(() -> {
            try {
                discoverySocket = new DatagramSocket(DISCOVERY_PORT);
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (!isStreaming) {
                    discoverySocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    Log.d(TAG, "Received discovery message: " + message);
                    // Parse HELLO packet and respond with HANDSHAKE
                    runOnUiThread(() -> statusText.setText("Found sender: " + packet.getAddress().getHostAddress()));
                }
            } catch (IOException e) {
                Log.e(TAG, "Discovery listener error", e);
            }
        }).start();
    }

    private void connectToSender(String ip) {
        new Thread(() -> {
            try {
                streamSocket = new DatagramSocket(STREAM_PORT);
                streamSocket.setSoTimeout(5000);

                runOnUiThread(() -> {
                    statusText.setText("Connecting to " + ip + ":" + STREAM_PORT);
                    connectButton.setEnabled(false);
                    stopButton.setEnabled(true);
                });

                isStreaming = true;

                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (isStreaming) {
                    try {
                        streamSocket.receive(packet);
                        // Process audio packet here
                        // TODO: Add Opus decoding and playback
                        Log.d(TAG, "Received audio packet from " + packet.getAddress());
                    } catch (IOException e) {
                        if (isStreaming) {
                            Log.e(TAG, "Stream error", e);
                            runOnUiThread(() -> statusText.setText("Stream error: " + e.getMessage()));
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection error", e);
                runOnUiThread(() -> {
                    statusText.setText("Connection failed: " + e.getMessage());
                    connectButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
            }
        }).start();
    }

    private void stopStreaming() {
        isStreaming = false;
        if (streamSocket != null) {
            streamSocket.close();
            streamSocket = null;
        }
        if (discoverySocket != null) {
            discoverySocket.close();
            discoverySocket = null;
        }

        runOnUiThread(() -> {
            statusText.setText("Stopped");
            connectButton.setEnabled(true);
            stopButton.setEnabled(false);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
    }
}

// layout file would go in res/layout/activity_main.xml
// TODO: Create simple UI with status, IP input, connect/stop buttons