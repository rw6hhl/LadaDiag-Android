// MainActivity.java — главный экран Android-приложения LadaDiag.
// Версия: V1.0 (15.10.2026)
// Назначение: управление подключением к ELM327, записью поездки,
//             сбросом ошибок (OBD-II Mode 04) и отправкой файлов на ПК.
// Среда: Android Studio, Java 17, minSdk 24, targetSdk 34.

package com.ladadiag.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ---------- Константы ----------
    private static final int REQUEST_PERMISSIONS = 100;

    // ---------- UI-элементы ----------
    private TextView statusText;
    private TextView dataText;
    private Button btnConnectElm;
    private Button btnStartTrip;
    private Button btnStopTrip;
    private Button btnClearDtc;
    private Button btnSendFiles;

    // ---------- Логика ----------
    private Elm327Client elmClient;
    private TripRecorder tripRecorder;
    private LocalSendSender localSendSender;
    private Handler uiHandler;

    // Флаг: идёт ли запись поездки.
    private boolean isTripRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация UI.
        statusText = findViewById(R.id.statusText);
        dataText = findViewById(R.id.dataText);
        btnConnectElm = findViewById(R.id.btnConnectElm);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnStopTrip = findViewById(R.id.btnStopTrip);
        btnClearDtc = findViewById(R.id.btnClearDtc);
        btnSendFiles = findViewById(R.id.btnSendFiles);

        uiHandler = new Handler(Looper.getMainLooper());

        // Инициализация логики.
        elmClient = new Elm327Client();
        tripRecorder = new TripRecorder(this);
        localSendSender = new LocalSendSender(this);

        // Запрос разрешений (Wi-Fi, хранилище).
        requestPermissionsIfNeeded();

        // ---------- Обработчик кнопки «Подключиться к ELM327» ----------
        btnConnectElm.setOnClickListener(v -> {
            statusText.setText(R.string.status_disconnected);
            // Подключение в фоновом потоке (сеть нельзя в UI-потоке).
            new Thread(() -> {
                boolean ok = elmClient.connect("192.168.0.10", 35000);
                uiHandler.post(() -> {
                    if (ok) {
                        statusText.setText(R.string.status_connected);
                        btnStartTrip.setEnabled(true);
                        btnClearDtc.setEnabled(true);
                        Toast.makeText(this, "ELM327 подключён", Toast.LENGTH_SHORT).show();
                    } else {
                        statusText.setText(R.string.status_disconnected);
                        Toast.makeText(this, "Не удалось подключиться к ELM327",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });

        // ---------- Обработчик кнопки «Начать поездку» ----------
        btnStartTrip.setOnClickListener(v -> {
            if (!elmClient.isConnected()) {
                Toast.makeText(this, R.string.msg_elm_not_connected,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            isTripRecording = true;
            tripRecorder.startTrip();
            statusText.setText(R.string.status_trip_recording);
            btnStartTrip.setEnabled(false);
            btnStopTrip.setEnabled(true);
            btnConnectElm.setEnabled(false);

            // Запуск цикла опроса PID (10 Гц).
            startPollingLoop();
        });

        // ---------- Обработчик кнопки «Остановить поездку» ----------
        btnStopTrip.setOnClickListener(v -> {
            isTripRecording = false;
            File csvFile = tripRecorder.stopTrip();
            statusText.setText(R.string.status_trip_stopped);
            btnStartTrip.setEnabled(true);
            btnStopTrip.setEnabled(false);
            btnConnectElm.setEnabled(true);
            if (csvFile != null) {
                Toast.makeText(this, "Файл сохранён: " + csvFile.getName(),
                        Toast.LENGTH_LONG).show();
            }
        });

        // ---------- Обработчик кнопки «Сбросить ошибки» ----------
        // Согласно требованию: кнопка встроена в главный экран.
        // Отправляет OBD-II команду 04 (Clear DTC).
        btnClearDtc.setOnClickListener(v -> {
            // Подтверждение — действие необратимое.
            new AlertDialog.Builder(this)
                    .setTitle(R.string.btn_clear_dtc)
                    .setMessage(R.string.msg_confirm_clear_dtc)
                    .setPositiveButton(R.string.msg_confirm_yes, (dialog, which) -> {
                        new Thread(() -> {
                            boolean ok = elmClient.clearDtc();
                            uiHandler.post(() -> {
                                if (ok) {
                                    statusText.setText(R.string.status_dtc_cleared);
                                    Toast.makeText(this, "Ошибки сброшены",
                                            Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(this, "Не удалось сбросить ошибки",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }).start();
                    })
                    .setNegativeButton(R.string.msg_confirm_no, null)
                    .show();
        });

        // ---------- Обработчик кнопки «Отправить файлы на ПК» ----------
        btnSendFiles.setOnClickListener(v -> {
            File tripDir = tripRecorder.getTripDirectory();
            if (tripDir == null || !tripDir.exists()) {
                Toast.makeText(this, "Нет файлов для отправки", Toast.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> {
                boolean ok = localSendSender.sendDirectory(tripDir);
                uiHandler.post(() -> {
                    if (ok) {
                        Toast.makeText(this, "Файлы отправлены на ПК",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Ошибка отправки. Проверьте LocalSend на ПК.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
    }

    // ---------- Цикл опроса PID ----------
    // Каждые 100 мс читает набор PID и пишет строку в CSV.
    private void startPollingLoop() {
        new Thread(() -> {
            while (isTripRecording) {
                // Чтение RPM (010C), Speed (010D), Coolant (0105),
                // Throttle (0111), Battery (0142).
                int rpm = elmClient.readPid("010C", 2);       // 2 байта
                int speed = elmClient.readPid("010D", 1);     // 1 байт
                int coolant = elmClient.readPid("0105", 1);   // 1 байт
                int throttle = elmClient.readPid("0111", 1);  // 1 байт
                int battObd = elmClient.readPid("0142", 2);   // 2 байта

                // Парсинг по формулам OBD-II.
                double rpmVal = PidParser.parseRpm(rpm);
                int speedVal = PidParser.parseSpeed(speed);
                int coolantVal = PidParser.parseCoolant(coolant);
                double throttleVal = PidParser.parseThrottle(throttle);
                double battVal = PidParser.parseBattery(battObd);

                // Запись строки в CSV.
                tripRecorder.writeSample(rpmVal, speedVal, coolantVal,
                        throttleVal, battVal);

                // Обновление UI (раз в 1 секунду — чтобы не перегружать).
                final String display = String.format(Locale.US,
                        "Обороты: %.0f\nСкорость: %d км/ч\n" +
                        "ОЖ: %d °C\nДроссель: %.1f %%\n" +
                        "Бортсеть: %.2f В",
                        rpmVal, speedVal, coolantVal,
                        throttleVal, battVal);
                uiHandler.post(() -> dataText.setText(display));

                // Пауза 100 мс (10 Гц).
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    // ---------- Запрос разрешений ----------
    private void requestPermissionsIfNeeded() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= 33) {
            perms = new String[]{
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE
            };
        } else {
            perms = new String[]{
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, perms, REQUEST_PERMISSIONS);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            // Разрешения запрошены. Обработка отказа — по факту.
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Корректное закрытие соединений.
        isTripRecording = false;
        if (tripRecorder != null) tripRecorder.stopTrip();
        if (elmClient != null) elmClient.disconnect();
        if (localSendSender != null) localSendSender.shutdown();
    }
}