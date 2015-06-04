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
public enum ModuleType {

    /**
     * Digital Humidity and Temperature sensor module
     */
    SW("SW"),SW2("SW2"),DHT("DHT"),ALL("ALL");
    
    private String shortCode;
    
    private ModuleType(String shortCode){
        this.shortCode = shortCode;
    }
    
    /**
     *
     * @param shortCode
     * @return the ModuleType with a matching short code
     */
    public static ModuleType fromShortCode(String shortCode) {
        for (ModuleType t : ModuleType.values())
            if (t.getShortCode().equals(shortCode))
                return t;
        
        return null;
    }

    /**
     * @return the shortCode
     */
    public String getShortCode() {
        return shortCode;
    }
}
