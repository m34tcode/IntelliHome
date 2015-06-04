/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.Date;

/**
 *
 * @author Josh
 */
public class Module {
    //1 - 253; 254: unassigned ID address; 255: broadcast address
    private int mID;
    private String alias = "";
    //0-254; 255: unassigned
    private int token;
    private ModuleType mType;
    private Date lastResponse = new Date();
    private boolean active = false;

    public Module(int mID, int token, ModuleType mType) {
        this.mID = mID;
        this.token = token;
        this.mType = mType;
    }

    /**
     * @return the mID
     */
    public int getmID() {
        return mID;
    }

    /**
     * @param mID the mID to set
     */
    public void setmID(int mID) {
        this.mID = mID;
    }

    /**
     * @return the mType
     */
    public ModuleType getmType() {
        return mType;
    }

    /**
     * @param mType the mType to set
     */
    public void setmType(ModuleType mType) {
        this.mType = mType;
    }

    /**
     * @return the lastResponse
     */
    public Date getLastResponseTime() {
        return lastResponse;
    }

    /**
     * @param lastResponse the lastResponse to set
     */
    public void resetLastResponseTime() {
        this.lastResponse = new Date();
    }

    /**
     * @return the token
     */
    public int getToken() {
        return token;
    }

    /**
     * @param token the token to set
     */
    public void setToken(int token) {
        this.token = token;
    }

    /**
     * @return the alias
     */
    public String getAlias() {
        return alias;
    }

    /**
     * @param alias the alias to set
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public String toString() {
        String stringVal = "[Module: ";
        stringVal += String.valueOf(mID);
        stringVal += ":T" + token;
        
        //stringVal += " [ TOKEN = " + token;
        stringVal += ", TYPE = " + mType.toString();
        if (!alias.equals("")){
            stringVal += ", ALIAS = " + alias;
        }
        stringVal += " ]";
        return stringVal;
    }

    /**
     * @return the active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @param active the active to set
     */
    public void setActive(boolean active) {
        this.active = active;
    }
    
}
