/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author Josh
 */
public final class Transaction implements TransactionEventListener {
    private final Integer[] moduleIDs;
    private final SerialTransmission transmission;
    private String sessionID = null;
    private final int timeout;
    private final Timer timeOutTimer;
    
    private final HashMap<Integer,String> responses;
    private final HashMap<Integer,Integer> moduleFailSends;
    private Integer retries = 0;

    private final List<TransactionEventListener> transactionEventListeners;
    private final List<TransactionResponseListener> transactionResponseListeners;
    
    /**
     * This creates a transaction which will record all responses to 
     * <code>transmission</code> from the receiving modules.
     * @param transmission The transmission associated with this transaction
     * @param moduleIDs The modules to transmit to
     */
    public Transaction(SerialTransmission transmission, Integer[] moduleIDs) {
        this.transactionEventListeners = new ArrayList<>();
        this.transactionResponseListeners = new ArrayList<>();
        this.timeout = GLOBAL.TRANSACTION_TIMEOUT;
        this.responses = new HashMap<>();
        this.moduleFailSends = new HashMap<>();
        this.moduleIDs = moduleIDs;
        this.transmission = transmission;
        
        //Initialize the response map with null values, to be later replaced with responses
        if (moduleIDs.length > 0 && moduleIDs[0] != 255)
            for (Integer i : this.moduleIDs){
                responses.put(i, null);
            }
        
        timeOutTimer = new Timer(true);
        addTransactionEventListener(this);
    }
    
    public void prepareNextRetry(){
        retries += 1;
    }
    
    private void setTimeoutTimer() {
        timeOutTimer.purge();
        timeOutTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                notifyTimedOut();
            }
        }, timeout);
    }
    
    public synchronized void cancelTimeoutTimer() {
        timeOutTimer.cancel();
        timeOutTimer.purge();
    }
    
    public SerialTransmission getTransmission(){
        return transmission;
    }
    
    public String getTID(){
        if (retries == 0)
            return getBaseTID();
        else {//if this is a retry then send the TID with an appended [A-Z] retry identifier
            return getBaseTID() + (char)('A' + retries - 1);
        }
    }
    
    public String getBaseTID(){
        return transmission.getTID();
    }
    
    public Integer[] getModuleIDs(){
        return moduleIDs;
    }
    
    public Integer[] getPendingModuleIDs(){
        List<Integer> pendingModules = new ArrayList<>();
        for (Integer id : moduleIDs){
            if (getResponses().get(id) == null)
                pendingModules.add(id);
        }
        return pendingModules.toArray(new Integer[0]);
    }
    
    public Integer[] getFinishedModuleIDs(){
        List<Integer> finishedModules = new ArrayList<>();
        for (Integer id : moduleIDs){
            if (getResponses().get(id) != null)
                finishedModules.add(id);
        }
        return finishedModules.toArray(new Integer[0]);
    }
    
    public void setResponse(Integer moduleID, String response){
        getResponses().put(moduleID,response);
        notifyResponseAdded(moduleID, response);
        
        //check if there are any modules pending responses, if so return, else notify finished
        for (String r : getResponses().values())
            if (r == null)
                return;
        
        notifyFinished();
    }
    
    //TODO create a custom listener for finished transactions
    //TODO call on finished listeners when finished

    /**
     *
     * @param al <code>TransactionEventListener</code> to add
     */
    public synchronized void addTransactionEventListener(TransactionEventListener al){
        transactionEventListeners.add(al);
    }
    /**
     *
     * @param al <code>TransactionEventListener</code> to add
     */
    public synchronized void removeTransactionEventListener(TransactionEventListener al){
        transactionEventListeners.remove(al);
    }
    /**
     *
     * @param al <code>TransactionEventListener</code> to add
     */
    public synchronized void addTransactionResponseListener(TransactionResponseListener al){
        transactionResponseListeners.add(al);
    }
    /**
     *
     * @param al <code>TransactionEventListener</code> to add
     */
    public synchronized void removeTransactionResponseListener(TransactionResponseListener al){
        transactionResponseListeners.remove(al);
    }
    
    public void notifyFinished(){
        synchronized (this) {
            for (TransactionEventListener l : transactionEventListeners) {
                l.onTransactionEvent(this,TransactionEvent.Type.FINISHED);
            }
        }
        cancelTimeoutTimer();
    }
    
    public void notifyTimedOut(){
        if (isBroadcast())
            notifyFinished(); //Broadcasts cannot fail
        else
            synchronized (this) {
                for (TransactionEventListener l : transactionEventListeners) {
                    l.onTransactionEvent(this,TransactionEvent.Type.TIMED_OUT);
                }
            }
    }
    
    public void notifyFailed(){
        synchronized (this) {
            for (TransactionEventListener l : transactionEventListeners) {
                l.onTransactionEvent(this,TransactionEvent.Type.FAILED);
            }
        }
    }
    
    public void notifyResponseAdded(Integer modID, String response){
        synchronized (this) {
            for (TransactionResponseListener l : transactionResponseListeners) {
                l.onResponseSet(this, modID, response);
            }
        }
    }
    
    public void notifyTransmitting() {
        setTimeoutTimer();
    }
    
    private static Integer IDToInt(String ID){
        return Integer.parseInt(ID, 36);
    }

    ///**
    // * @return the timeoutAction
    // */
    //public TransactionEventAction getTimeoutAction() {
    //    return onFinishedAction;
    //}
    
    /**
     * @param moduleID module ID
     * @return the number of times the hub has failed to transmit the message to this module
     */
    @Deprecated
    public int getModuleFailSendCount(int moduleID){
        return moduleFailSends.get(moduleID);
    }
    
    /**
     * @param moduleID module ID
     * @return the number of times the hub has failed to transmit the message to this module
     */
    @Deprecated
    public int incrementModuleFailSendCount(int moduleID){
        moduleFailSends.put(moduleID,moduleFailSends.get(moduleID) + 1);
        return moduleFailSends.get(moduleID);
    }

    ///**
    // * @param onFinishedAction the timeoutAction to set
    // */
    //public void setTimeoutAction(TransactionEventAction onFinishedAction) {
    //    this.onFinishedAction = onFinishedAction;
    //}

    /**
     * @return the retries
     */
    public Integer getRetries() {
        return retries;
    }
    
    public synchronized boolean isBroadcast() {
        return moduleIDs.length == 1 && moduleIDs[0] == 255;
    }

    @Override
    public void onTransactionEvent(Transaction tran, TransactionEvent.Type eType) {
        //TODO implement something or remove implementation
    }

    /**
     * @return the sessionID
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * @param sessionID the sessionID to set
     */
    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    /**
     * @return the responses
     */
    public HashMap<Integer,String> getResponses() {
        return responses;
    }
}
