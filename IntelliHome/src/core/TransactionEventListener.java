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
public abstract interface TransactionEventListener {
    /**
     *
     * @param tran Transaction
     * @param eType Event type
     */
    public abstract void onTransactionEvent(Transaction tran, TransactionEvent.Type eType);
}
