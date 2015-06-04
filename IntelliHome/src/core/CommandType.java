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
public enum CommandType {
    SET("S"), SET_RESPONSE("SR"), GET("G"), GET_RESPONSE("GR"), COMMAND("C"), COMMAND_RESPONSE("CR"), NOTIFICATION_RESPONSE("NR"), DATA_RESPONSE("DR");
    
    private final String shortCode;

    private CommandType(String shortCode) {
        this.shortCode = shortCode;
    }
    
    /**
     *
     * @param shortCode
     * @return the CommandType with a matching short code
     */
    public static CommandType fromShortCode(String shortCode) {
        for (CommandType t : CommandType.values())
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
