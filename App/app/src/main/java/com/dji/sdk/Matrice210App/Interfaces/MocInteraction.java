package com.dji.sdk.Matrice210App.Interfaces;

import dji.common.error.DJIError;

public interface MocInteraction {
    void sendData(String data);

    void sendData(byte[] data);

    void dataReceived(byte[] bytes);
    void onResult(DJIError djiError);
}