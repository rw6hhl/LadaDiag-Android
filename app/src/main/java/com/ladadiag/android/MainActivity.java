// MainActivity.java — главный экран Android-приложения LadaDiag.
// Версия: V1.2 (15.10.2026)
// Изменения V1.2: добавлен FLAG_KEEP_SCREEN_ON (экран не гаснет,
//   пока работает приложение). Метод connect() вызывается с контекстом.
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
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

    private static final int REQUEST_PERMISSIONS = 100;
    private static final String PREFS_NAME = "LadaDiagPrefs";
    private static final String KEY_CAR_NUMBER = "car_number";
    private static final String KEY_PHONE = "phone";

    private EditText editCarNumber;
    private EditText editPhone;
    private TextView statusText;
    private TextView dataText;
    private Button btnConnectElm;
    private Button btnStartTrip;
    private Button btnStopTrip;
    private Button btnClearDtc;
    private Button btnSendFiles;

    private Elm327Client elmClient;
    private TripRecorder tripRecorder;
    private LocalSendSender localSendSender;
    private Handler uiHandler;
    private SharedPreferences prefs;

    private boolean isTripRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Не давать экрану гаснуть, пока приложение на переднем плане.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        editCarNumber = findViewById(R.id.editCarNumber);
        editPhone = findViewById(R.id.editPhone);
        statusText = findViewById(R.id.statusText);
        dataText = findViewById(R.id.dataText);
        btnConnectElm = findViewById(R.id.btnConnectElm);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnStopTrip = findViewById(R.id.btnStopTrip);
        btnClearDtc = findViewById(R.id.btnClearDtc);
        btnSendFiles = findViewById(R.id.btnSendFiles);

        uiHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        editCarNumber.setText(prefs.getString(KEY_CAR_NUMBER, ""));
        editPhone.setText(prefs.getString(KEY_PHONE, ""));

        elmClient = new Elm327Client();
        tripRecorder = new TripRecorder(this);
        localSendSender = new LocalSendSender(this);

        requestPermissionsIfNeeded();

        btnConnectElm.setOnClickListener(v -> {
            statusText.setText(R.string.status_disconnected);
            new Thread(() -> {
                // Передаём контекст для привязки сокета к Wi-Fi адаптера.
                boolean ok = elmClient.connect(MainActivity.this, "192.168.0.10", 35000);
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

        btnStartTrip.setOnClickListener(v -> {
            if (!elmClient.isConnected()) {
                Toast.makeText(this, R.string.msg_elm_not_connected,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String carNumber = editCarNumber.getText().toString().trim();
            if (TextUtils.isEmpty(carNumber)) {
                Toast.makeText(this, R.string.msg_enter_car_number,
                        Toast.LENGTH_SHORT).show();
                editCarNumber.requestFocus();
                return;
            }
            String phone = editPhone.getText().toString().trim();
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(this, R.string.msg_enter_phone,
                        Toast.LENGTH_SHORT).show();
                editPhone.requestFocus();
                return;
            }

            prefs.edit()
                 .putString(KEY_CAR_NUMBER, carNumber)
                 .putString(KEY_PHONE, phone)
                 .apply();

            isTripRecording = true;
            tripRecorder.startTrip(carNumber, phone);
            statusText.setText(R.string.status_trip_recording);
            btnStartTrip.setEnabled(false);
            btnStopTrip.setEnabled(true);
            btnConnectElm.setEnabled(false);
            editCarNumber.setEnabled(false);
            editPhone.setEnabled(false);

            startPollingLoop();
        });

        btnStopTrip.setOnClickListener(v -> {
            isTripRecording = false;
            File csvFile = tripRecorder.stopTrip();
            statusText.setText(R.string.status_trip_stopped);
            btnStartTrip.setEnabled(true);
            btnStopTrip.setEnabled(false);
            btnConnectElm.setEnabled(true);
            editCarNumber.setEnabled(true);
            editPhone.setEnabled(true);
            if (csvFile != null) {
                Toast.makeText(this, "Файл сохранён: " + csvFile.getName(),
                        Toast.LENGTH_LONG).show();
            }
        });

        btnClearDtc.setOnClickListener(v -> {
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

    private void startPollingLoop() {
        new Thread(() -> {
            while (isTripRecording) {
                int rpm = elmClient.readPid("010C", 2);
                int speed = elmClient.readPid("010D", 1);
                int coolant = elmClient.readPid("0105", 1);
                int throttle = elmClient.readPid("0111", 1);
                int battObd = elmClient.readPid("0142", 2);

                double rpmVal = PidParser.parseRpm(rpm);
                int speedVal = PidParser.parseSpeed(speed);
                int coolantVal = PidParser.parseCoolant(coolant);
                double throttleVal = PidParser.parseThrottle(throttle);
                double battVal = PidParser.parseBattery(battObd);

                tripRecorder.writeSample(rpmVal, speedVal, coolantVal,
                        throttleVal, battVal);

                final String display = String.format(Locale.US,
                        "Обороты: %.0f\nСкорость: %d км/ч\n" +
                        "ОЖ: %d °C\nДроссель: %.1f %%\n" +
                        "Бортсеть: %.2f В",
                        rpmVal, speedVal, coolantVal,
                        throttleVal, battVal);
                uiHandler.post(() -> dataText.setText(display));

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTripRecording = false;
        if (tripRecorder != null) tripRecorder.stopTrip();
        if (elmClient != null) elmClient.disconnect();
        if (localSendSender != null) localSendSender.shutdown();
    }
}