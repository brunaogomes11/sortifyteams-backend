package com.gomesdev.sortifyteams.domain.versaoapp;

/**
 * Metadados extraídos do {@code AndroidManifest.xml} de um APK (spec 002).
 *
 * <p>{@code versionCode}, {@code versionName} e o {@code runtimeVersion} do
 * Expo já vêm gravados no próprio arquivo — não faz sentido pedir para o
 * admin digitar de novo algo que pode divergir por erro de digitação do que
 * o build realmente contém.
 */
public record ApkManifest(int versionCode, String versionName, String runtimeVersion) {
}
