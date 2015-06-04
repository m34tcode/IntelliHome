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
public class SerialData {
    private int mID;
    private String TID;
    private ModuleType mType;
    private String data;

    public SerialData(int mID, String TID, ModuleType mType, String data) {
        this.mID = mID;
        this.TID = TID;
        this.mType = mType;
        this.data = data;
    }

    /**
     * @return the mID
     */
    public int getmID() {
        return mID;
    }

    /**
     * @return the mType
     */
    public ModuleType getmType() {
        return mType;
    }

    /**
     * @return the data
     */
    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "SerialData(mID: " + mID + ", TID: " + TID + ", mType: " + mType + ", data: " + data + ")";
    }

    /**
     * @return the TID
     */
    public String getTID() {
        return TID;
    }
    
}
