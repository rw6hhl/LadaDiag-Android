// LocalSendSender.java — отправка файлов на ПК через LocalSend.
// Версия: V1.0 (15.10.2026)
// Назначение: подготовка файлов поездок к отправке через LocalSend.
//             LocalSend — это отдельное приложение, оно устанавливается
//             на Android и ПК. Данный класс формирует список файлов
//             и открывает LocalSend через Intent для передачи.
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalSendSender {

    private static final String TAG = "LocalSendSender";

    // Пакет LocalSend в Google Play / F-Droid.
    private static final String LOCALSEND_PACKAGE = "org.localsend.localsend_app";

    private final Context context;

    public LocalSendSender(Context context) {
        this.context = context;
    }

    /**
     * Отправка всех CSV-файлов из каталога через LocalSend.
     * Открывает LocalSend с предустановленным списком файлов.
     *
     * @param tripDirectory Каталог с файлами поездок.
     * @return true, если Intent успешно отправлен.
     */
    public boolean sendDirectory(File tripDirectory) {
        if (tripDirectory == null || !tripDirectory.exists()) {
            Log.e(TAG, "Каталог не существует: " + tripDirectory);
            return false;
        }

        // Сбор всех CSV-файлов.
        File[] files = tripDirectory.listFiles((dir, name) -> name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            Log.e(TAG, "Нет CSV-файлов в каталоге");
            return false;
        }

        // Формирование списка URI через FileProvider.
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            try {
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", f);
                uris.add(uri);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка получения URI для " + f.getName(), e);
            }
        }

        if (uris.isEmpty()) {
            Log.e(TAG, "Не удалось получить URI ни для одного файла");
            return false;
        }

        // Формирование Intent для LocalSend (ACTION_SEND_MULTIPLE).
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("text/csv");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setPackage(LOCALSEND_PACKAGE);

        try {
            context.startActivity(intent);
            Log.i(TAG, "LocalSend запущен с " + uris.size() + " файлами");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "LocalSend не установлен или ошибка: " + e.getMessage(), e);
            // Fallback: открыть общий диалог «Поделиться».
            Intent fallback = new Intent(Intent.ACTION_SEND_MULTIPLE);
            fallback.setType("text/csv");
            fallback.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(fallback,
                    "Отправить файлы через…"));
            return true;
        }
    }

    /**
     * Освобождение ресурсов (заглушка — LocalSend не держит соединений).
     */
    public void shutdown() {
        // LocalSend — отдельное приложение, ресурсы освобождать не нужно.
        Log.i(TAG, "LocalSendSender завершён");
    }
}