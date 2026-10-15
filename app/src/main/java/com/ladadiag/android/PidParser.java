// PidParser.java — парсер ответов OBD-II PID.
// Версия: V1.0 (15.10.2026)
// Назначение: преобразование сырых hex-значений PID в физические величины
//             по формулам стандарта OBD-II (SAE J1979).
// Среда: Android Studio, Java 17.

package com.ladadiag.android;

public class PidParser {

    /**
     * PID 010C — обороты двигателя.
     * Формула: ((A * 256) + B) / 4.
     *
     * @param raw Сырое значение (2 байта, объединённые в int).
     * @return Обороты в об/мин.
     */
    public static double parseRpm(int raw) {
        if (raw < 0) return 0;
        return raw / 4.0;
    }

    /**
     * PID 010D — скорость автомобиля.
     * Формула: A.
     *
     * @param raw Сырое значение (1 байт).
     * @return Скорость в км/ч.
     */
    public static int parseSpeed(int raw) {
        return Math.max(raw, 0);
    }

    /**
     * PID 0105 — температура охлаждающей жидкости.
     * Формула: A - 40.
     *
     * @param raw Сырое значение (1 байт).
     * @return Температура в °C.
     */
    public static int parseCoolant(int raw) {
        if (raw < 0) return 0;
        return raw - 40;
    }

    /**
     * PID 0111 — положение дроссельной заслонки.
     * Формула: (A * 100) / 255.
     *
     * @param raw Сырое значение (1 байт).
     * @return Положение в %.
     */
    public static double parseThrottle(int raw) {
        if (raw < 0) return 0;
        return (raw * 100.0) / 255.0;
    }

    /**
     * PID 0142 — напряжение бортовой сети.
     * Формула: ((A * 256) + B) / 1000.
     *
     * @param raw Сырое значение (2 байта, объединённые в int).
     * @return Напряжение в В.
     */
    public static double parseBattery(int raw) {
        if (raw < 0) return 0;
        return raw / 1000.0;
    }
}