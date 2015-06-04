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
public class SerialTransmission {

    private static int intID = 0;
    
    
    public static String getNextID(){
        String id = Integer.toString(intID, 36);
        intID++;
        
        if (Integer.toString(intID, 36).length() > GLOBAL.TID_LENGTH) {
            intID = 0;
        }
        
        return padl(id,"0",GLOBAL.TID_LENGTH);
    }

    private static String padl(String id, String pad, int targetLen) {
        String nid = id;
        for (int i = nid.length(); i < targetLen; i++){
            nid = pad + nid;
        }
        return nid;
    }
    
    public enum DIRECTION {
        INCOMING, OUTGOING;
    }
    
    private DIRECTION dir;
    private int mID;
    private final String TID;
    
    private ModuleType mType;
    private CommandType cType;
    private String msg;
    
    public SerialTransmission(DIRECTION dir, int mID, ModuleType mType, CommandType cType, String msg) {
        this(dir, getNextID(), mID, mType, cType, msg);
    }

    //no reason to expose TID paramater yet?
    public SerialTransmission(DIRECTION dir, String TID, int mID, ModuleType mType, CommandType cType, String msg) {
        this.dir = dir;
        this.TID = TID;
        this.mID = mID;
        this.mType = mType;
        this.cType = cType;
        this.msg = msg;
    }

    /**
     * @return whether this is an incoming or outgoing transmission
     */
    public DIRECTION getDir() {
        return dir;
    }

    /**
     * @return the module ID
     */
    public int getmID() {
        return mID;
    }

    /**
     * @param mID the new module ID
     */
    public void setmID(int mID) {
        this.mID = mID;
    }

    /**
     * @return the transaction ID
     */
    public String getTID() {
        return TID;
    }

    /**
     * @return the module Type
     */
    public ModuleType getmType() {
        return mType;
    }

    /**
     * @param mType the new module type
     */
    public void setmType(ModuleType mType) {
        this.mType = mType;
    }

    /**
     * @return the command type
     */
    public CommandType getcType() {
        return cType;
    }

    /**
     * @param cType the new command type
     */
    public void setcType(CommandType cType) {
        this.cType = cType;
    }

    /**
     * @return the message
     */
    public String getMsg() {
        return msg;
    }

    /**
     * @param msg the new message
     */
    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String serialize() throws Exception{
        String result = "" + mID + ";" + TID + ";" + cType.getShortCode() + ";" + msg;
        if (result.length() > GLOBAL.NRF_MAX_MSG_LEN)
            throw new Exception("This objects serial form is too long: " + result);
        return result;
    }
    
    public SerialTransmission cloneWithNewToken(String newTK){
        SerialTransmission tran = new SerialTransmission(dir, newTK, mID, mType, cType, msg);
        return tran;
   }
    
    public SerialTransmission clone(int newID){
        SerialTransmission tran = new SerialTransmission(dir, TID, newID, mType, cType, msg);
        return tran;
    }

    @Override
    public String toString() {
        return "SerialTransmition(mID: " + mID + ", TID: " + TID + ", mType: " + mType + ", cType: " + cType + ", DIR: " + dir + ", msg: " + msg + ")";
    }
    
}
