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
public class SerialNotification {

    public enum Notifications {
        INIT("I"),INIT_R("I*");
        private String shortcode;

        private Notifications(String shortcode) {
            this.shortcode = shortcode;
        }

        public static Notifications fromShortCode(String s){
            for (Notifications n : Notifications.values()){
                if (n.shortcode.equals(s))
                    return n;
            }
            return null;
        }
        /**
         * @return the shortcode
         */
        public String getShortcode() {
            return shortcode;
        }
        
        
    }
    
    private int mID;
    private ModuleType mType;
    private Notifications nType;
    private String msg;

    public SerialNotification(int mID, ModuleType mType, Notifications nType, String msg) {
        this.mID = mID;
        this.mType = mType;
        this.nType = nType;
        this.msg = msg;
    }
    
    /**
     * @return the mID
     */
    public int getmID() {
        return mID;
    }

    /**
     * @return the msg
     */
    public String getMsg() {
        return msg;
    }

    /**
     * @return the nType
     */
    public Notifications getnType() {
        return nType;
    }

    /**
     * @return the mType
     */
    public ModuleType getmType() {
        return mType;
    }

    @Override
    public String toString() {
        return "SerialNotification(mID: " + mID + ", mType: " + mType + ", nType: " + nType + ", msg: " + msg + ")";
    }
}
