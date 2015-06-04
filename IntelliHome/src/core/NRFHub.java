/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;

/**
 *
 * @author Josh
 */
//TODO notificationeventlistener unused/ included w/serial object???
public class NRFHub implements FailSendEventListener, SerialObjectListener, TransactionEventListener {

    //settings for getModules function
    public enum ModuleInitFilter {
        PENDING_MODULES, INITIALIZED_MODULES, ALL_MODULES;
    }
    
    private SerialInterface si;
    private WebConnection webCon;
    
    private final HashMap<ModuleType, Manager> managers = new HashMap<>();
    private final Timer pendingModuleTimeoutTimer = new Timer();
    private final Timer ModuleListObserverTimer = new Timer();
    private final List<Transaction> pendingTransactions = new ArrayList<>();
    
    private final List<Module> initializedModules = new ArrayList<>();
    private final List<Module> pendingInitModules = new ArrayList<>();
    
    public NRFHub() {
        pendingModuleTimeoutTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                List<Module> timedOutModules = new ArrayList<>();//TODO decide on functionality for timedout modules
                for (Module m : getModules(ModuleInitFilter.ALL_MODULES)){
                    if ((Date.from(Instant.now()).getTime() - m.getLastResponseTime().getTime() 
                            >= GLOBAL.MODULE_ALIVE_TIMEOUT)
                            && m.isActive())
                        timedOutModules.add(m);
                }
                /*getModules(ModuleInitFilter.ALL_MODULES).stream()
                        .filter((m) -> (Date.from(Instant.now()).getTime() - m.getLastResponseTime().getTime() 
                            >= GLOBAL.MODULE_ALIVE_TIMEOUT)
                            && m.isActive())
                        .forEach((m) -> {
                            timedOutModules.add(m);
                        });*/
                
            }
        }, 10000, 30000);
        ModuleListObserverTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                //webCon.
            }
        }, 5000, 5000);
    }
    
    /**
     * @return the webCon
     */
    public WebConnection getWebConnection() {
        return webCon;
    }

    /**
     * @param webCon the webCon to set
     */
    public void setWebConnection(WebConnection webCon) {
        this.webCon = webCon;
    }
    
    @Override
    public void onFailSend(String TID, int modID) {
        for (Transaction t : pendingTransactions){
            if (TID.substring(0,GLOBAL.TID_LENGTH).equals(t.getBaseTID())){
                SerialTransmission tran;
                Module m;
                if ((m = getModule(modID)) != null)
                    tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, t.getTID(), modID, m.getmType(), t.getTransmission().getcType(), t.getTransmission().getMsg());
                else
                    tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, t.getTID(), modID, ModuleType.ALL, t.getTransmission().getcType(), t.getTransmission().getMsg());
                transmit(tran, false);
                break;
            }
        }
    }

    private void handleClientResponse(SerialTransmission tran) {
        Transaction matchedTran;
        for (Transaction T : pendingTransactions){
            if (T.getBaseTID().equals(tran.getTID().subSequence(0, GLOBAL.TID_LENGTH))){
                matchedTran = T;
                if (tran.getcType() == CommandType.SET_RESPONSE && tran.getMsg().startsWith("ID,")){
                    Matcher m = Pattern.compile("(?<OID>\\d{1,3}),(?<OTK>\\d{1,3}),(?<NID>\\d{1,3}),(?<NTK>\\d{1,3})").matcher(tran.getMsg());
                    if (m.find()){
                        matchedTran.setResponse(Integer.parseInt(m.group("OID")), tran.getMsg());
                        Module mod = getModule(Integer.parseInt(m.group("OID")),Integer.parseInt(m.group("OTK")),ModuleInitFilter.ALL_MODULES);
                        if (mod != null){
                            mod.setmID(Integer.parseInt(m.group("NID")));
                            mod.setToken(Integer.parseInt(m.group("NTK")));
                        }
                    } else
                        System.out.println("Failed to parse a Set ID response");
                } else if (tran.getcType() == CommandType.COMMAND_RESPONSE && tran.getMsg().startsWith("SETUP,")){
                    Matcher m = Pattern.compile("(?<OID>\\d{1,3}),(?<OTK>\\d{1,3})").matcher(tran.getMsg());
                    if (m.find()){
                        matchedTran.setResponse(Integer.parseInt(m.group("OID")), tran.getMsg());
                        Module mod = getModule(Integer.parseInt(m.group("OID")),Integer.parseInt(m.group("OTK")),ModuleInitFilter.ALL_MODULES);
                        if (mod != null)
                            mod.setmID(tran.getmID());
                        else
                            System.out.println("Null module discovered... ahhhhhh");
                    } else
                        System.out.println("Failed to parse a setup response");
                } else
                    matchedTran.setResponse(tran.getmID(), tran.getMsg());
                return;
            }
        }
        //No pending transaction with a matching ID... Log this error
        System.out.println("Module response recieved with an unknown transaction ID: " + tran);
    }

    private void validateModuleTransmission(Module mod, ModuleType type) {
        validateModuleTransmission(mod, type, null);
    }

    private void validateModuleTransmission(Module mod, ModuleType type, String token) {
        if (mod.getmType() != type) {
            System.out.println("Module type does not match recorded "
                    + "module type(" + type + ") for this ID: " + mod);
            //TODO implement some rectification here
        }
        
        if (token != null && !String.valueOf(mod.getToken()).equals(token)){
            System.out.println("Module token does not match recorded "
                    + "token(" + token + ") for this module: " + mod);
            //TODO implement some rectification here
        }
        
        mod.resetLastResponseTime();
        mod.setActive(true);
    }
    
    /**
     * @return the si
     */
    private SerialInterface getSerialInterface() {
        return si;
    }
    
    public void SetSerialInterface(SerialInterface iface){
        si = iface;
        if (getSerialInterface() != null){
            getSerialInterface().serialSubscribe(this);
        }
    }
    
    /**
     *
     * @param transmission the <code>Transmission</code> to send
     * @param modules modules to transmit to
     * @param createTransaction if true, a new transaction will be created with this transmission
     * @return If <code>createTransaction</code> is true, return the <code>Transaction</code> created. Otherwise null
     */
    public synchronized final Transaction transmit(SerialTransmission transmission, Integer[] modules, boolean createTransaction){
        Transaction transaction = null;
        if (getSerialInterface() != null){
            if (createTransaction){
                transaction = new Transaction(transmission, modules);
                transaction.addTransactionEventListener(this);
                pendingTransactions.add(transaction);
                transaction.notifyTransmitting();
            }
            getSerialInterface().transmit(transmission, modules);
        } else {
            System.out.println("Tried to transmit from a null Serial Interface!");
            System.exit(1);
        }
        return transaction;
    }
    
    /**
     * 
     * @param transmission The <code>Transmission </code>to send
     * @param createTransaction if true, a new transaction will be created with this transmission
     * @return If <code>createTransaction</code> is true, return the <code>Transaction</code> created. Otherwise null
     */
    public synchronized final Transaction transmit(SerialTransmission transmission, boolean createTransaction){
        return transmit(transmission, new Integer[] {transmission.getmID()},createTransaction);
    }
    
    /**
     * This function will return the first matching alias found in the list of 
     * known modules, or the module with an ID matching <code>name</code>, if it is an integer.
     * @param name alias or <code>String</code> ID of the specified module.
     * @param token the token of the target module
     * @param filter the filter to apply to this search
     * @return the first matching module or <code>null</code>
     */
    public Module getModule(String name, Integer token, ModuleInitFilter filter){
        Integer intID;
        if (name.matches("\\d*")) {
            intID = Integer.parseInt(name);
            return getModule(intID,token,filter);
        }
        
        for (Module m : getModules(filter))
            if (m.getAlias().equals(name) && m.getToken() == token)
                return m;
        return null;
    }
    
    /**
     * This function will return the first matching alias found in the list of 
     * known modules, or the module with an ID matching <code>name</code>, if it is an integer.
     * @param name alias or <code>String</code> ID of the specified module.
     * @param token the token of the target module
     * @param filter the filter to apply to this search
     * @return the first matching module or <code>null</code>
     */
    public Module getModule(String name, ModuleInitFilter filter){
        Integer intID;
        if (name.matches("\\d*")) {
            intID = Integer.parseInt(name);
            return getModule(intID,filter);
        }
        
        for (Module m : getModules(filter))
            if (m.getAlias().equals(name))
                return m;
        return null;
    }
    
    public synchronized final void execute(String command){
        //if this is a base command
        Matcher hubMatcher = Pattern.compile("(?<SSID>[-,a-zA-Z0-9]{1,128}):\\$\\.(?:Modules?\\[(?<modules>(?:\\w{1,}(?:;\\w{1,})?,?)*)\\]|(?<allModules>AllModules)|(?<allModulesI>AllModulesI)|(?<allModulesP>AllModulesP))\\.(?<command>\\w*)(?:\\[(?<commandParam>(?:\\w*,?)*)\\])?").matcher(command);
        if (hubMatcher.find()){            
            List<Module> targetModules = new ArrayList<>();
        
            if (hubMatcher.group("allModules") != null)
                targetModules.addAll(getModules(ModuleInitFilter.ALL_MODULES));
            else if (hubMatcher.group("allModulesP") != null)
                targetModules.addAll(getModules(ModuleInitFilter.PENDING_MODULES));
            else if (hubMatcher.group("allModulesI") != null)
                targetModules.addAll(getModules(ModuleInitFilter.INITIALIZED_MODULES));
            else if (hubMatcher.group("modules") != null) {
                targetModules.addAll(getModules(hubMatcher.group("modules").split(","), NRFHub.ModuleInitFilter.ALL_MODULES));
            } else
                return;//should never happen...
            Transaction trans;
            SerialTransmission tran;
            List<Integer> targetModuleIDs = new ArrayList<>();
            switch (hubMatcher.group("command")){
                case "SetAlias":
                    System.out.println("Executing SetAlias");
                    if (hubMatcher.group("commandParam") == null) {
                        System.out.println("Invalid paramater passed to Set Alias: Null Paramater");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"Invalid paramater passed to Set Alias: Null Paramater");
                        return;
                    }
                    if (targetModules.size() > 1){
                        System.out.println("Only one alias can be set at a time. A multi-set was attempted!");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"Only one alias can be set at a time. A multi-set was attempted!");
                        return;
                    }
                    if (targetModules.size() < 1){
                        System.out.println("No valid modules were targeted for this command");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"No valid modules were targeted for this command");
                        return;
                    }
                    targetModules.get(0).setAlias(hubMatcher.group("commandParam"));
                    break;
                case "SetID":
                    System.out.println("Executing SetID");
                    if (hubMatcher.group("commandParam") == null || !hubMatcher.group("commandParam").matches("\\d{1,3},\\d{1,3}")) {
                        System.out.println("Invalid paramater passed to Set ID: Paramater did not conform to the '<NewID>,<New Token>' pattern");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"Invalid paramater passed to Set ID: Paramater did not conform to the '<NewID>,<New Token>' pattern");
                        return;
                    }
                    if (targetModules.size() > 1){
                        System.out.println("Only one ID/Token can be set at a time. A multi-set was attempted!");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"Only one ID/Token can be set at a time. A multi-set was attempted!");
                        return;
                    }
                    if (targetModules.size() < 1){
                        System.out.println("No valid modules were targeted for this command");
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules,"No valid modules were targeted for this command");
                        return;
                    }
                    String[] split = hubMatcher.group("commandParam").split(",");
                    Integer newMID = Integer.parseInt(split[0]);
                    Integer newToken = Integer.parseInt(split[1]);
                    String cmdMsg = "ID," + targetModules.get(0).getToken() + "," + newMID + "," + newToken;
                    tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, targetModules.get(0).getmID(), ModuleType.DHT, CommandType.SET, cmdMsg);
                    trans = transmit(tran, true);
                    trans.addTransactionEventListener(new TransactionEventListener() {

                        @Override
                        public void onTransactionEvent(Transaction tran, TransactionEvent.Type eType) {
                            if (eType.equals(TransactionEvent.Type.FAILED))
                                System.out.println("Failed to set ID/token of module " + tran.getModuleIDs()[0]);
                            if (eType.equals(TransactionEvent.Type.FINISHED) || eType.equals(TransactionEvent.Type.FAILED))
                                webCon.writeCommandResultToWeb(tran.getSessionID(), ModuleType.ALL, "SetID", webCon.getTransactionModuleListJSON(tran));;
                        }
                    });
                    trans.setSessionID(hubMatcher.group("SSID"));
                    break;
                case "Setup":
                    System.out.println("Executing Setup");
                    List<Module> preSetupRequestedModules = new ArrayList<>();
                    targetModuleIDs = new ArrayList<>();
                    for (Module m : targetModules){
                        //make sure there are no pending requests to halt this module already, if so, break out of function
                        for (Transaction pt : pendingTransactions)
                            if (Arrays.asList(pt.getModuleIDs()).contains(m.getmID())
                                    && pt.getTransmission().getMsg().equals("SETUP")){
                                preSetupRequestedModules.add(m);
                            }
                    }
                    targetModules.removeAll(preSetupRequestedModules);
                        
                    for (Module mod : targetModules){
                        targetModuleIDs.add(mod.getmID());
                    }
                    if (preSetupRequestedModules.size() > 0){
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), targetModules, "Some modules had pending requests to setup, so the request was not repeated");
                    }
                    tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.ALL, CommandType.COMMAND, "SETUP");
                    trans = transmit(tran, targetModuleIDs.toArray(new Integer[] {}), true);
                    trans.setSessionID(hubMatcher.group("SSID"));
                    trans.addTransactionEventListener((Transaction tran1, TransactionEvent.Type eType) -> {
                        if (eType.equals(TransactionEvent.Type.FINISHED) || eType.equals(TransactionEvent.Type.FAILED))
                            webCon.writeCommandResultToWeb(tran1.getSessionID(), ModuleType.ALL, "Setup", webCon.getTransactionModuleListJSON(tran1));
                    });
                    return;
                case "Init":
                    System.out.println("Executing Init");
                    List<Module> preInitializedModules = new ArrayList<>();
                    targetModuleIDs = new ArrayList<>();
                    for (Module m : targetModules){
                        //if a module with this id is already initialised abort so we dont create a conflict
                        if (getModule(m.getmID(),ModuleInitFilter.INITIALIZED_MODULES) != null || m.getmID() > 253 || m.getmID() < 1){
                            preInitializedModules.add(m);
                            continue;
                        }
                        targetModules.removeAll(preInitializedModules);
                        removeModule(m, ModuleInitFilter.PENDING_MODULES);
                        addModule(m, ModuleInitFilter.INITIALIZED_MODULES);
                    }
                    if (preInitializedModules.size() > 0)
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), preInitializedModules, "Some modules were already initialized, or had not yet been assigned a valid ID(valid ranges from 1 to 253)");
                    tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.ALL, CommandType.COMMAND, "BEGIN");
                    trans = transmit(tran, targetModuleIDs.toArray(new Integer[]{}), true);
                    trans.setSessionID(hubMatcher.group("SSID"));
                    trans.addTransactionEventListener((Transaction transaction, TransactionEvent.Type eType) -> {
                        if (eType.equals(TransactionEvent.Type.FINISHED) || eType.equals(TransactionEvent.Type.FAILED))
                            webCon.writeCommandResultToWeb(transaction.getSessionID(), ModuleType.ALL, "Init", webCon.getTransactionModuleListJSON(transaction));
                    });
                    return;
                case "Uninit":
                    System.out.println("Executing Uninit");
                    for (Module m : targetModules){
                        removeModule(m, ModuleInitFilter.INITIALIZED_MODULES);
                        addModule(m, ModuleInitFilter.PENDING_MODULES);
                    } //fall through \/
                //case "Halt":
                    List<Module> pendingHaltModules = new ArrayList<>();
                    List<Integer> modIDs = new ArrayList<>();
                    for (Module m : targetModules)
                        for (Transaction pt : pendingTransactions)
                            if (Arrays.asList(pt.getModuleIDs()).contains(m.getmID())
                                    && pt.getTransmission().getMsg().equals("HALT")){
                                pendingHaltModules.add(m);
                                targetModules.remove(m);
                            }
                    for (Module mod : targetModules){
                        targetModuleIDs.add(mod.getmID());
                    }
                    if (pendingHaltModules.size() > 0)
                        webCon.writeCommandErrorToWeb(hubMatcher.group("SSID"), ModuleType.ALL, hubMatcher.group("command"), pendingHaltModules, "Some modules had previously been requested to halt already.");
                    SerialTransmission tm = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.ALL, CommandType.COMMAND, "HALT");
                    trans = transmit(tm, modIDs.toArray(new Integer[] {}), true);
                    trans.setSessionID(hubMatcher.group("SSID"));
                    trans.addTransactionEventListener((Transaction transaction, TransactionEvent.Type eType) -> {
                        if (eType.equals(TransactionEvent.Type.FINISHED) || eType.equals(TransactionEvent.Type.FAILED))
                            webCon.writeCommandResultToWeb(transaction.getSessionID(), ModuleType.ALL, "Halt", webCon.getTransactionModuleListJSON(transaction));
                    });
            }
            
        }
        
        //if the command is a regular command to be processed by a manager
        //regCommand format is <manager shortcode>.commandFoo1.commandFoo2.commandFoo3
        Matcher regCommand = Pattern.compile("(?<SSID>[-,a-zA-Z0-9]{1,128}):(?<MTYPE>\\w*?)\\.").matcher(command);
        
        if (regCommand.find()){
            //TODO log debug level that a command was parsed, and what it was
            Manager man = managers.get(ModuleType.valueOf(regCommand.group("MTYPE")));
            if (man != null)
                man.execute(command);
            else {
                //TODO log that there was no matching manager for this module type
            }
        }
    }
    
    /**
     *
     * @param mID module ID
     * @return The Module with a matching ID, or null if there is none
     */
    public Module getModule(int mID){
        return getModule(mID, ModuleInitFilter.ALL_MODULES);
    }
    
    /**
     * 
     * @param moduleIdentifiers names or IDS of modules
     * @param filter the filter to apply
     * @return the list of modules matchings IDs/Aliases and the specified filter
     */
    public List<Module> getModules(String[] moduleIdentifiers, ModuleInitFilter filter){
        List<Module> mods = new ArrayList<>();
        for (String name : moduleIdentifiers){
            Module m;
            if (name.contains(";")){
                String[] split = name.split(";");
                m = getModule(split[0], Integer.parseInt(split[1]), filter);
            } else
                m = getModule(name, filter);
            if (m != null)
                mods.add(m);
            else
                System.out.println("Failed to fetch module with ID/Alias(and token if applicable) matching '" + name + "'");
        }
        return mods;
    }
    
    /**
     *
     * @param mID module ID
     * @param filter A ModuleInitFilter designating which modules to include in the search
     * @return The Module with a matching ID, or null if there is none
     */
    public Module getModule(int mID, ModuleInitFilter filter){
        for (Module m : getModules(filter)){
            if (m.getmID() == mID)
                return m;
        }
        return null;
    }
    
    /**
     *
     * @param mID module ID
     * @param token module token
     * @param filter A ModuleInitFilter designating which modules to include in the search
     * @return The Module with a matching ID, or null if there is none
     */
    public Module getModule(int mID, int token, ModuleInitFilter filter){
        for (Module m : getModules(filter)){
            if (m.getmID() == mID
                    && m.getToken() == token)
                return m;
        }
        return null;
    }

    /**
     * @param filter the filter to apply to the list of modules
     * @return the modules
     */
    public synchronized List<Module> getModules(ModuleInitFilter filter) {
        List<Module> modList = new ArrayList<>();
        switch(filter){
            case ALL_MODULES:
                modList.addAll(initializedModules);
                modList.addAll(pendingInitModules);
                break;
            case INITIALIZED_MODULES:
                modList.addAll(initializedModules);
                break;
            case PENDING_MODULES:
                modList.addAll(pendingInitModules);
                break;
        }
        return modList;
    }

    private void addModule(Module module, ModuleInitFilter filter) {
        switch(filter){
            case INITIALIZED_MODULES:
                initializedModules.add(module);
                notifyManagersModulesChanged();
                break;
            case PENDING_MODULES:
                pendingInitModules.add(module);
                break;
        }
    }
    
    public void removeModule(Module module, ModuleInitFilter filter) {
        switch(filter){
            case INITIALIZED_MODULES:
                initializedModules.remove(module);
                notifyManagersModulesChanged();
                break;
            case PENDING_MODULES:
                pendingInitModules.remove(module);
                break;
        }
    }
    
    public void deregisterModule(Module m){
        removeModule(m, ModuleInitFilter.INITIALIZED_MODULES);
        addModule(m, ModuleInitFilter.PENDING_MODULES);
        
        haltModule(m);
    }
    
    
    /**
     * This function will attempt to initialize module <code>m</code>, if there is no module with the same ID currently initialized
     * @param m module to initialize
     * @return true if this module was initialized
     */
    public synchronized boolean initModule(Module m){
        //if a module with this id is already initialised abort so we dont create a conflict
        if (getModule(m.getmID(),ModuleInitFilter.INITIALIZED_MODULES) != null || m.getmID() > 253 || m.getmID() < 1)
            return false;
        
        removeModule(m, ModuleInitFilter.PENDING_MODULES);
        addModule(m, ModuleInitFilter.INITIALIZED_MODULES);
        
        SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, m.getmID(), m.getmType(), CommandType.COMMAND, "BEGIN");
        transmit(tran, true);
        return true;
    }
    
    public synchronized Transaction haltModule(Module m){
        //make sure there are no pending requests to halt this module already, if so, break out of function
        for (Transaction pt : pendingTransactions)
            if (Arrays.asList(pt.getModuleIDs()).contains(m.getmID())
                    && pt.getTransmission().getMsg().equals("HALT"))
                return null;
        
        SerialTransmission tm = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, m.getmID(), m.getmType(), CommandType.COMMAND, "HALT");
        return transmit(tm, true);
    }

    public void forceSetupModule(Module m) {
        //make sure there are no pending requests to halt this module already, if so, break out of function
        for (Transaction pt : pendingTransactions)
            if (Arrays.asList(pt.getModuleIDs()).contains(m.getmID())
                    && pt.getTransmission().getMsg().equals("SETUP"))
                return;
        
        SerialTransmission tm = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, m.getmID(), m.getmType(), CommandType.COMMAND, "SETUP");
        transmit(tm, true);
    }
    
    @Override
    public void serialDataRecieved(SerialData data) {
        
        //if this module is not yet initialized then ignore it
        if (getModule(data.getmID(),ModuleInitFilter.INITIALIZED_MODULES) == null) {
            System.out.println("Uninitialized module sent data: " + data);
            SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, data.getmID(), ModuleType.ALL,  CommandType.COMMAND, "HALT");
            transmit(tran,true);
            return;
        }
        
        Module mod = getModule(data.getmID(), ModuleInitFilter.ALL_MODULES);
        validateModuleTransmission(mod,data.getmType());
        
        Manager m = managers.get(data.getmType());
        if (m != null)
            m.handle(data);
    }
    
    @Override
    public void serialNotificationRecieved(SerialNotification notification) {
        Module existingModule;
        if ((notification.getnType() == SerialNotification.Notifications.INIT
                || notification.getnType() == SerialNotification.Notifications.INIT_R)
                && notification.getMsg().matches("\\d*")){
            if ((existingModule = getModule(notification.getmID(), Integer.parseInt(notification.getMsg()), ModuleInitFilter.PENDING_MODULES)) != null) {//Module initializing with an already known
                System.out.println("Pending module with ID " + notification.getmID() + " repeated its request to init");
                haltModule(existingModule);
            } else if ((existingModule = getModule(notification.getmID(), Integer.parseInt(notification.getMsg()), ModuleInitFilter.INITIALIZED_MODULES)) != null) {
                if (existingModule.getToken() == Integer.parseInt(notification.getMsg())){
                    System.out.println("Possible ID/Token conflict detected. ID:" + notification.getmID() + "  TK:" + notification.getMsg());//TODO log that module with same id and token as a recent registered module is requesting to init 
                } else {
                    System.out.println("Module trying to initialize with an already taken ID(token does not match recorded module)\n  ID: " + notification.getmID());
                }
            } else { //This module is not recorded as pending or initialized yet, so lets add it to list of pending
                Module mod = new Module(notification.getmID(), Integer.parseInt(notification.getMsg()), notification.getmType());
                addModule(mod, ModuleInitFilter.PENDING_MODULES);
                haltModule(mod);
            }
            return;
        }//end init modules code
        
        if (getModule(notification.getmID(),ModuleInitFilter.INITIALIZED_MODULES) == null) {
            System.out.println("Uninitialized module sent a notification: " + notification);
            SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, notification.getmID(), ModuleType.ALL,  CommandType.COMMAND, "HALT");
            transmit(tran,true);
            return;
        }
        
        Module mod = getModule(notification.getmID(), ModuleInitFilter.ALL_MODULES);
        validateModuleTransmission(mod,notification.getmType());
        
        Manager m = managers.get(notification.getmType());
        if (m != null)
            m.handle(notification);
    }

    
    @Override
    public void serialTransmissionRecieved(SerialTransmission tran) {
        
        //<editor-fold defaultstate="collapsed" desc="Old code">
        //if transaction is a ID get response
//        if (tran.getMsg().matches(";GR;ID,.*")){
//            Matcher match = Pattern.compile("ID,(?<ID>\\w*),(?<TOKEN>\\w*)").matcher(tran.getMsg());
//            Module firstMod = getModule(tran.getmID());
//            if (firstMod != null){
//                if (firstMod.getToken().equals(tran.getMsg()))//TODO tell all modules with this ID to reset tokens and respond back?
//                    return; //a known module was just restating its ID, carry on
//                else {
//                    //tell this module to change its ID as we already have a module with this ID(token doesnt match what we have on record)
//                    byte nID = getnewID();//TODO make sure generated token below is unique
//                    SerialTransmission nTran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, tran.getmID(), tran.getmType(), CommandType.COMMAND, "ID," + tran.getMsg()/*the old token*/ + "," + nID + "," + GLOBAL.rand.nextInt(255)/* new token */);
//                }
//            } else {//no known module with this ID, lets store it
//                Module m = new Module(tran.getmID(), match.group("TOKEN"), tran.getmType());
//            }
//
//
//}</editor-fold>
        
        if (tran.getcType() == CommandType.COMMAND_RESPONSE
                || tran.getcType() == CommandType.GET_RESPONSE
                || tran.getcType() == CommandType.SET_RESPONSE){
            handleClientResponse(tran);
            return;
        }
        
        //if this module is not yet initialized then ignore it
        if (getModule(tran.getmID(),ModuleInitFilter.INITIALIZED_MODULES) == null) {
            System.out.println("Uninitialized module sent a message: " + tran);
            SerialTransmission tm = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, tran.getmID(), ModuleType.ALL,  CommandType.COMMAND, "HALT");
            transmit(tm,true);
            return;
        }
        
        Module mod = getModule(tran.getmID(), ModuleInitFilter.ALL_MODULES);
        validateModuleTransmission(mod,tran.getmType());
        
        Manager m = managers.get(tran.getmType());
        if (m != null)
            m.handle(tran);
    }

    
    public void registerManager(ModuleType mType, Manager man){
        managers.put(mType, man);
    }
    
    public void deregisterManager(ModuleType mType){
        managers.remove(mType);
    }
    
    private void notifyManagersModulesChanged(){
        for (Manager m : managers.values()) {
            m.updateModuleList(getModules(ModuleInitFilter.INITIALIZED_MODULES)); //TODO do module managers need to know about unitialized modules? not atm...
        }
    }

    
    @Override
    public void onTransactionEvent(Transaction tran, TransactionEvent.Type eType) {
        if (eType.equals(TransactionEvent.Type.FINISHED)) {
            System.out.println("Transaction with TID " + tran.getBaseTID() + " finished");
            pendingTransactions.remove(tran);
        } else if (eType.equals((TransactionEvent.Type.TIMED_OUT))){
            System.out.println("Transaction timed out with TID " + tran.getTID());
            if (tran.getRetries() < GLOBAL.NRF_MAX_RETRIES){//TODO NRF retries should be sent with a new ID each time to ensure a response from client
                tran.prepareNextRetry();
                tran.notifyTransmitting();
                transmit(tran.getTransmission().cloneWithNewToken(tran.getTID()),tran.getPendingModuleIDs(), false);
            } else {
                tran.notifyFailed();
            }
        } else if (eType.equals(TransactionEvent.Type.FAILED)){
            //TODO log that we failed to transmit to still pending modules
            System.out.println("Failed to transmit to modules.");
            System.out.println("  Modules: " + Arrays.toString(tran.getPendingModuleIDs()));
            System.out.println("  Transmission: " + tran.getTransmission().getMsg());
            tran.cancelTimeoutTimer();
            pendingTransactions.remove(tran);
        }
    }

    private byte getNewID() {
        for (byte b = 0 ; b < 255; b++){
            if (getModule(b) == null)
                return b;
        }
        return -1;
    }
    
    public void checkForInactiveModules(List<Module> testees){
        SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.ALL, CommandType.GET, "S");
        List<Integer> testeeIDs =  new ArrayList<>();
        for (Module m : testees){
            testeeIDs.add(m.getmID());
        }
        
        System.out.println("testeeIDS: " + testeeIDs.toString());
        
        Transaction transaction = transmit(tran, testeeIDs.toArray(new Integer[] {}), true);
        transaction.addTransactionEventListener(new TransactionEventListener() {

            @Override
            public void onTransactionEvent(Transaction tran, TransactionEvent.Type eType) {
                if (eType.equals(TransactionEvent.Type.FAILED)){
                    for (Integer mid : tran.getPendingModuleIDs()){
                        Module m;
                        if ((m = getModule(mid,ModuleInitFilter.INITIALIZED_MODULES)) != null){
                            m.setActive(false);
                        }
                    }
                }
            }
        });
    }
    
}