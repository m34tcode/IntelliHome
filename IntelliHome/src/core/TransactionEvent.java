/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.awt.AWTEvent;

/**
 *
 * @author Josh
 */
public class TransactionEvent {

    
    //Event types
    
    public enum Type {
        TIMED_OUT, FINISHED, FAILED;
    }
    
    private Transaction tran;

    
    public TransactionEvent(Transaction tran) {
        this.tran = tran;
    }

    public TransactionEvent() {
    }

    /**
     * @return the tran
     */
    public Transaction getTran() {
        return tran;
    }

    /**
     * @param tran the tran to set
     */
    public void setTran(Transaction tran) {
        this.tran = tran;
    }
    
    
}
