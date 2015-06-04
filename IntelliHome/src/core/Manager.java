/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.List;

/**
 *
 * @author Josh
 */
public abstract interface Manager {
    
    
    public abstract boolean handles(ModuleType mType);
    public abstract void handle(SerialData data);
    public abstract void handle(SerialNotification notification);
    public abstract void handle(SerialTransmission tran);
    public abstract boolean execute(String command);
    
    public abstract void updateModuleList(List<Module> modules);
}
