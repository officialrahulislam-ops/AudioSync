package com.audiosync.receiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AudioSyncReceiver"
        private const val DISCOVERY_PORT = 19826
        private const val STREAM_PORT = 19827
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var statusText: TextView
    private lateinit var ipEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var stopButton: Button

    private var discoverySocket: DatagramSocket? = null
    private var streamSocket: DatagramSocket? = null
    private var isStreaming = false
    private var streamingThread: Thread? = null
    private var discoveryThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipEditText = findViewById(R.id.ipEditText)
        connectButton = findViewById(R.id.connectButton)
        stopButton = findViewById(R.id.stopButton)

        checkPermissions()

        connectButton.setOnClickListener {
            val ip = ipEditText.text.toString().trim()
            if (ip.isNotEmpty()) {
                connectToSender(ip)
            } else {
                Toast.makeText(this, "Please enter IP address", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener { stopStreaming() }
        startDiscoveryListener()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startDiscoveryListener() {
        discoveryThread = Thread {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT)
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (!isStreaming) {
                    try {
                        discoverySocket?.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "Received discovery message: $message from ${packet.address.hostAddress}")
                        runOnUiThread {
                            statusText.text = "Found sender: ${packet.address.hostAddress}"
                        }
                        // TODO: Send HANDSHAKE response
                    } catch (e: IOException) {
                        if (isStreaming) break
                        Log.e(TAG, "Discovery receive error", e)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Discovery listener error", e)
            }
        }
        discoveryThread?.start()
    }

    private fun connectToSender(ip: String) {
        streamingThread = Thread {
            try {
                streamSocket = DatagramSocket(STREAM_PORT)
                streamSocket?.soTimeout = 5000

                runOnUiThread {
                    statusText.text = "Connecting to $ip:$STREAM_PORT"
                    connectButton.isEnabled = false
                    stopButton.isEnabled = true
                }

                isStreaming = true

                val buffer = ByteArray(4096)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isStreaming) {
                    try {
                        streamSocket?.receive(packet)
                        // Process audio packet here
                        // TODO: Add Opus decoding and playback
                        Log.d(TAG, "Received audio packet from ${packet.address} length=${packet.length}")
                    } catch (e: IOException) {
                        if (isStreaming) {
                            Log.e(TAG, "Stream error", e)
                            runOnUiThread { statusText.text = "Stream error: ${e.message}" }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection error", e)
                runOnUiThread {
                    statusText.text = "Connection failed: ${e.message}"
                    connectButton.isEnabled = true
                    stopButton.isEnabled = false
                }
            }
        }
        streamingThread?.start()
    }

    private fun stopStreaming() {
        isStreaming = false
        streamSocket?.close()
        streamSocket = null
        discoverySocket?.close()
        discoverySocket = null

        runOnUiThread {
            statusText.text = "Stopped"
            connectButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}