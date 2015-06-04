/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

/**
 *
 * @author Josh
 */


public abstract interface SerialObjectListener {
    public abstract void serialNotificationRecieved(SerialNotification notification);
    public abstract void serialDataRecieved(SerialData data);
    public abstract void serialTransmissionRecieved(SerialTransmission tran);
}
