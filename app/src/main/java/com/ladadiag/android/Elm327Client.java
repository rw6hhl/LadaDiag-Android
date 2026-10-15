// Elm327Client.java — TCP-клиент для ELM327 Wi-Fi.
// Версия: V1.0 (15.10.2026)
// Назначение: подключение к ELM327 по Wi-Fi (TCP), отправка AT-команд
//             и OBD-II PID, чтение ответов, сброс ошибок (Mode 04).
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

public class Elm327Client {

    private static final String TAG = "Elm327Client";

    // Таймаут соединения (мс).
    private static final int CONNECT_TIMEOUT_MS = 5000;
    // Таймаут чтения ответа (мс).
    private static final int READ_TIMEOUT_MS = 3000;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean connected = false;

    /**
     * Подключение к ELM327 по Wi-Fi.
     *
     * @param host IP-адрес ELM327 (обычно 192.168.0.10).
     * @param port Порт (обычно 35000).
     * @return true, если подключение успешно.
     */
    public synchronized boolean connect(String host, int port) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            connected = true;

            // Инициализация ELM327.
            sendCommand("ATZ");      // Сброс адаптера.
            Thread.sleep(500);        // Пауза после сброса.
            sendCommand("ATE0");      // Отключение эха.
            sendCommand("ATL0");      // Отключение переводов строк.
            sendCommand("ATH0");      // Отключение заголовков.
            sendCommand("ATSP6");     // Протокол ISO 15765-4 CAN (11 бит, 500 кбит/с).
            sendCommand("ATAT1");     // Автоматическая настройка таймаутов.
            Thread.sleep(100);

            Log.i(TAG, "ELM327 подключён: " + host + ":" + port);
            return true;

        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Ошибка подключения: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * Отправка AT-команды или OBD-II PID.
     *
     * @param command Команда (например, "010C").
     * @return Ответ ELM327 без пробелов и переводов строк.
     */
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
            // Чтение до символа '>' (prompt) или до таймаута.
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

    /**
     * Чтение OBD-II PID.
     *
     * @param pid  PID (например, "010C").
     * @param len  Ожидаемая длина данных (1 или 2 байта).
     * @return Числовое значение (сырое, до парсинга).
     */
    public synchronized int readPid(String pid, int len) {
        String response = sendCommand(pid);
        if (response.isEmpty() || response.contains("NO DATA")
                || response.contains("ERROR") || response.contains("UNABLE")) {
            return -1;
        }
        try {
            // Ответ формата "41 0C 1A F8" — берём байты после "41 0C".
            // После отправки AT H0 (headers off) ответ: "410C1AF8".
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

    /**
     * Сброс ошибок (OBD-II Mode 04 — Clear DTC).
     * Отправляет команду "04" в CAN-шину автомобиля.
     *
     * @return true, если команда выполнена успешно.
     */
    public synchronized boolean clearDtc() {
        String response = sendCommand("04");
        // Успешный ответ: "44" (positive response to Mode 04)
        // или пустой ответ (некоторые ELM327 не отвечают на Mode 04).
        boolean ok = response.isEmpty() || response.startsWith("44");
        Log.i(TAG, "Clear DTC: " + response + " → " + (ok ? "OK" : "FAIL"));
        return ok;
    }

    /**
     * Проверка состояния подключения.
     */
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }

    /**
     * Отключение от ELM327.
     */
    public synchronized void disconnect() {
        connected = false;
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