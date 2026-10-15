// Elm327Client.java — TCP-клиент для ELM327 Wi-Fi.
// Версия: V1.2 (15.10.2026)
// Изменения V1.2: добавлена привязка сокета к Wi-Fi-сети адаптера
//   (bindProcessToNetwork). Это устраняет проблему, когда Android
//   маршрутизирует трафик через мобильные данные, хотя Wi-Fi подключён.
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Elm327Client {

    private static final String TAG = "Elm327Client";

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 3000;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected = false;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    /**
     * Подключение к ELM327 по Wi-Fi с привязкой к сети адаптера.
     *
     * @param context Контекст приложения (для ConnectivityManager).
     * @param host    IP-адрес ELM327 (обычно 192.168.0.10).
     * @param port    Порт (обычно 35000).
     * @return true, если подключение успешно.
     */
    public synchronized boolean connect(Context context, String host, int port) {
        // --- Привязка к Wi-Fi-сети адаптера ---
        // Android по умолчанию использует мобильные данные, если Wi-Fi без интернета.
        // bindProcessToNetwork заставляет все TCP-соединения идти через Wi-Fi адаптера.
        try {
            connectivityManager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    connectivityManager.bindProcessToNetwork(network);
                    Log.i(TAG, "Сокет привязан к Wi-Fi-сети адаптера");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Не удалось привязать сокет к Wi-Fi: " + e.getMessage());
        }

        // --- Подключение TCP ---
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            connected = true;

            sendCommand("ATZ");
            Thread.sleep(500);
            sendCommand("ATE0");
            sendCommand("ATL0");
            sendCommand("ATH0");
            sendCommand("ATSP6");
            sendCommand("ATAT1");
            Thread.sleep(100);

            Log.i(TAG, "ELM327 подключён: " + host + ":" + port);
            return true;

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Ошибка подключения: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    public synchronized String sendCommand(String command) {
        if (!connected || writer == null || reader == null) {
            return "";
        }
        try {
            writer.print(command + "\r");
            writer.flush();

            StringBuilder sb = new StringBuilder();
            String line;
            long startTime = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.contains(">")) {
                    sb.append(line.replace(">", "").trim());
                    break;
                }
                sb.append(line.trim());
                if (System.currentTimeMillis() - startTime > READ_TIMEOUT_MS) {
                    break;
                }
            }
            String result = sb.toString().replaceAll("\\s", "");
            Log.d(TAG, "CMD: " + command + " → " + result);
            return result;
        } catch (IOException e) {
            Log.e(TAG, "Ошибка отправки команды: " + e.getMessage(), e);
            return "";
        }
    }

    public synchronized int readPid(String pid, int len) {
        String response = sendCommand(pid);
        if (response.isEmpty() || response.contains("NO DATA")
                || response.contains("ERROR") || response.contains("UNABLE")) {
            return -1;
        }
        try {
            if (response.length() < 4 + len * 2) {
                return -1;
            }
            String dataHex = response.substring(4, 4 + len * 2);
            return Integer.parseInt(dataHex, 16);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Ошибка парсинга PID " + pid + ": " + response, e);
            return -1;
        }
    }

    public synchronized boolean clearDtc() {
        String response = sendCommand("04");
        boolean ok = response.isEmpty() || response.startsWith("44");
        Log.i(TAG, "Clear DTC: " + response + " → " + (ok ? "OK" : "FAIL"));
        return ok;
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    public synchronized void disconnect() {
        connected = false;
        // Отвязка от сети адаптера (возврат к мобильным данным).
        try {
            if (connectivityManager != null) {
                connectivityManager.bindProcessToNetwork(null);
            }
        } catch (Exception e) {
            Log.w(TAG, "Не удалось отвязать сокет: " + e.getMessage());
        }
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка закрытия: " + e.getMessage(), e);
        }
        reader = null;
        writer = null;
        socket = null;
    }
}