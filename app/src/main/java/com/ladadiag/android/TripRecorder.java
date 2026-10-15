// TripRecorder.java — запись CSV-файла поездки.
// Версия: V1.2 (15.10.2026)
// Изменения V1.2 (относительно V1.1):
//   - Путь сохранения изменён с Environment.getExternalStorageDirectory()
//     на context.getExternalFilesDir(null) — это «песочница» приложения,
//     работает на всех Android без разрешений (Scoped Storage).
//   - Добавлена проверка: файл реально создан (csvFile.exists()).
//   - Имя файла формируется как <госномер>_<NNN>.csv.
//   - В CSV добавляются метаданные: госномер и телефон владельца.
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class TripRecorder {

    private static final String TAG = "TripRecorder";

    private static final String CSV_HEADER = "ts;rpm;speed;coolant;throttle;batt_obd";
    private static final String CSV_DELIMITER = ";";
    private static final String CSV_LINE_END = "\r\n";

    private final Context context;
    private File tripDirectory;
    private File csvFile;
    private FileWriter writer;
    private boolean isRecording = false;
    private long tripStartTime;

    private String carNumber = "";
    private String phoneNumber = "";

    public TripRecorder(Context context) {
        this.context = context;
        // «Песочница» приложения:
        // /storage/emulated/0/Android/data/com.ladadiag.android/files/trips/
        // Работает на всех Android без разрешений.
        this.tripDirectory = new File(context.getExternalFilesDir(null), "trips");
    }

    /**
     * Начало записи поездки.
     * Имя файла: <госномер>_<NNN>.csv (например, в113ур26_001.csv).
     *
     * @param carNumber Госномер машины (например, "в113ур26").
     * @param phone     Телефон владельца (например, "+79123456789").
     * @return true, если файл успешно создан; false — если ошибка.
     */
    public synchronized boolean startTrip(String carNumber, String phone) {
        this.carNumber = carNumber != null ? carNumber.trim() : "";
        this.phoneNumber = phone != null ? phone.trim() : "";

        try {
            if (!tripDirectory.exists()) {
                boolean created = tripDirectory.mkdirs();
                if (!created && !tripDirectory.exists()) {
                    Log.e(TAG, "Не удалось создать папку: " + tripDirectory);
                    return false;
                }
            }

            String safeCarNumber = sanitizeFileName(this.carNumber);
            int nextNumber = findNextTripNumber(safeCarNumber);
            String fileName = String.format(Locale.US, "%s_%03d.csv",
                    safeCarNumber, nextNumber);
            csvFile = new File(tripDirectory, fileName);

            writer = new FileWriter(csvFile, false);

            writer.write("# car_number=" + this.carNumber + CSV_LINE_END);
            writer.write("# phone=" + this.phoneNumber + CSV_LINE_END);
            writer.write("# started_at=" + System.currentTimeMillis() + CSV_LINE_END);

            writer.write(CSV_HEADER + CSV_LINE_END);
            writer.flush();

            // Проверка: файл реально создан.
            if (!csvFile.exists()) {
                Log.e(TAG, "Файл не создан: " + csvFile.getAbsolutePath());
                return false;
            }

            tripStartTime = System.currentTimeMillis();
            isRecording = true;
            Log.i(TAG, "Начало записи: " + csvFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Ошибка старта записи: " + e.getMessage(), e);
            isRecording = false;
            return false;
        }
    }

    private int findNextTripNumber(String safeCarNumber) {
        int maxNumber = 0;
        File[] files = tripDirectory.listFiles();
        if (files == null) {
            return 1;
        }
        String prefix = safeCarNumber + "_";
        for (File f : files) {
            String name = f.getName();
            if (!name.startsWith(prefix) || !name.endsWith(".csv")) {
                continue;
            }
            String middle = name.substring(prefix.length(), name.length() - 4);
            try {
                int n = Integer.parseInt(middle);
                if (n > maxNumber) {
                    maxNumber = n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxNumber + 1;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    public synchronized void writeSample(double rpm, int speed, int coolant,
                                          double throttle, double battObd) {
        if (!isRecording || writer == null) {
            return;
        }
        try {
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
        Log.i(TAG, "Запись остановлена: " +
                (csvFile != null ? csvFile.getName() : "null"));
        return csvFile;
    }

    public File getTripDirectory() {
        return tripDirectory;
    }

    public File getCurrentFile() {
        return csvFile;
    }
}