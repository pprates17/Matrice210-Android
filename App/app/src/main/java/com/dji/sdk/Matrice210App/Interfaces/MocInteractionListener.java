package com.dji.sdk.Matrice210App.Interfaces;

public interface MocInteractionListener {
    void sendData(final String data);
    void sendData(final byte[] data);
}