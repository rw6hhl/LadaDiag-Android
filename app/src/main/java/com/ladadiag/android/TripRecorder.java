// TripRecorder.java — запись CSV-файла поездки.
// Версия: V1.0 (15.10.2026)
// Назначение: накопление данных о поездке в CSV-файл
//             (совместимый с ESP32-версией) для последующей передачи на ПК.
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TripRecorder {

    private static final String TAG = "TripRecorder";

    // Заголовок CSV — совместим с ESP32-версией (config.h V1.1).
    // ESP32 пишет: ts;rpm;speed;coolant;throttle;fuel;batt_obd;batt_adc;...
    // Android пишет подмножество: ts;rpm;speed;coolant;throttle;batt_obd
    private static final String CSV_HEADER = "ts;rpm;speed;coolant;throttle;batt_obd";
    private static final String CSV_DELIMITER = ";";
    private static final String CSV_LINE_END = "\r\n";

    private final Context context;
    private File tripDirectory;
    private File csvFile;
    private FileWriter writer;
    private boolean isRecording = false;
    private long tripStartTime;

    public TripRecorder(Context context) {
        this.context = context;
        this.tripDirectory = new File(
                Environment.getExternalStorageDirectory(),
                "LadaDiag/trips");
    }

    /**
     * Начало записи поездки.
     * Создаёт CSV-файл с именем, содержащим дату и время.
     */
    public synchronized void startTrip() {
        try {
            if (!tripDirectory.exists()) {
                tripDirectory.mkdirs();
            }
            // Имя файла: trip_YYYY-MM-DD_HH-MM-SS.csv
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss", Locale.US);
            String fileName = "trip_" + sdf.format(new Date()) + ".csv";
            csvFile = new File(tripDirectory, fileName);

            writer = new FileWriter(csvFile, false);
            writer.write(CSV_HEADER + CSV_LINE_END);
            writer.flush();

            tripStartTime = System.currentTimeMillis();
            isRecording = true;
            Log.i(TAG, "Начало записи: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Ошибка старта записи: " + e.getMessage(), e);
            isRecording = false;
        }
    }

    /**
     * Запись одной строки данных.
     *
     * @param rpm      Обороты (об/мин).
     * @param speed    Скорость (км/ч).
     * @param coolant  Температура ОЖ (°C).
     * @param throttle Положение дросселя (%).
     * @param battObd  Напряжение бортсети (В).
     */
    public synchronized void writeSample(double rpm, int speed, int coolant,
                                          double throttle, double battObd) {
        if (!isRecording || writer == null) {
            return;
        }
        try {
            // Метка времени — миллисекунды от начала поездки
            // (совместимо с ESP32, где ts = millis()).
            long ts = System.currentTimeMillis() - tripStartTime;
            String line = String.format(Locale.US,
                    "%d;%.0f;%d;%d;%.1f;%.2f",
                    ts, rpm, speed, coolant, throttle, battObd);
            writer.write(line + CSV_LINE_END);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Ошибка записи: " + e.getMessage(), e);
        }
    }

    /**
     * Остановка записи поездки.
     *
     * @return CSV-файл поездки или null при ошибке.
     */
    public synchronized File stopTrip() {
        isRecording = false;
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
                writer = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Ошибка закрытия файла: " + e.getMessage(), e);
        }
        Log.i(TAG, "Запись остановлена: " + (csvFile != null ? csvFile.getName() : "null"));
        return csvFile;
    }

    /**
     * Каталог с файлами поездок (для отправки через LocalSend).
     */
    public File getTripDirectory() {
        return tripDirectory;
    }

    /**
     * Текущий CSV-файл поездки.
     */
    public File getCurrentFile() {
        return csvFile;
    }
}