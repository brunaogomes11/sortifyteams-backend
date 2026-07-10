package com.gomesdev.sortifyteams.config.geo;

/** Coordenadas + cidade resolvidas a partir de um endereço (OpenStreetMap). */
public record GeocodeResult(double latitude, double longitude, String cidade) {
}
