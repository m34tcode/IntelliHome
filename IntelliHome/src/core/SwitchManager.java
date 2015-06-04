/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;

/**
 *
 * @author Josh_2
 */
public class SwitchManager implements Manager, TransactionEventListener {

    
    private final List<Module> switchModules = new ArrayList<>();
    private NRFHub hub;
    @Override
    public boolean handles(ModuleType mType) {
        return mType.equals(ModuleType.SW) || mType.equals(ModuleType.SW2);
    }

    @Override
    public void handle(SerialData data) {//TODO implement, although this should never execute
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void handle(SerialNotification notification) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void handle(SerialTransmission tran) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean execute(String command) {
        /*
        Commands:
          Toggle switch SW.Module
          Activate Switch
          Deactivate Switch
          Get Switch State
        */
        Matcher m = Pattern.compile("(?<SSID>\\w*):SW.(?:Modules?\\[(?<modules>(?:\\w*,?)*)\\]|(?<allModules>AllModules))\\.(?<command>\\w*)(?:\\[(?<commandParam>\\w*)\\])?").matcher(command);
        
        List<Module> targetModules = new ArrayList<>();
        
        if (m.find()){
            if (m.group("allModules") != null)
                targetModules.addAll(switchModules);
            else {
                targetModules.addAll(hub.getModules(m.group("modules").split(","), NRFHub.ModuleInitFilter.INITIALIZED_MODULES));
            }
        }
        
        Integer[] modules = new Integer[targetModules.size()];
        for (int i = 0; i < targetModules.size(); i++)
            modules[i] = targetModules.get(i).getmID();
        
        String state = m.group("commandParam");
        SerialTransmission tran;
        Transaction t;
        switch (m.group("command")){
            case "SetState":
                tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.SW2, CommandType.SET, "S," + state);
                t = getHub().transmit(tran, modules, true);
                t.addTransactionEventListener(this);
                t.setSessionID(m.group("SSID"));
                t.addTransactionResponseListener(new TransactionResponseListener() {
                    @Override
                    public void onResponseSet(Transaction tran, Integer modID, String response) {
                        if (response != null && !response.matches("S,[10\\*]*")){
                            tran.setResponse(modID, null);
                        }
                    }
                });
                return true;
            case "GetState":
                tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 0, ModuleType.SW2, CommandType.GET, state);
                t = getHub().transmit(tran, modules, true);
                t.addTransactionEventListener(this);
                t.setSessionID(m.group("SID"));
                t.addTransactionResponseListener(new TransactionResponseListener() {
                    @Override
                    public void onResponseSet(Transaction tran, Integer modID, String response) {
                        if (response != null && !response.matches("S,[10\\*]*")){
                            tran.setResponse(modID, null);
                        }
                    }
                });
                return true;
            /*case "Activate":
                break;
            case "Deactivate":
                break;
            case "Toggle":
               break;/**/
            default:
                System.out.println("Invalid command recieved");
                new Throwable().printStackTrace(System.err);
        }
        return false;
    }

    @Override
    public void updateModuleList(List<Module> modules) {
        switchModules.clear();
        for (Module m : modules) 
            if (handles(m.getmType()))
                switchModules.add(m);
    }

    /**
     * @return the hub
     */
    public NRFHub getHub() {
        return hub;
    }

    /**
     * @param hub the hub to set
     */
    public void setHub(NRFHub hub) {
        this.hub = hub;
    }

    @Override
    public void onTransactionEvent(Transaction tran, TransactionEvent.Type eType) {
        if (eType == TransactionEvent.Type.TIMED_OUT)
            return;
        JSONObject json = new JSONObject();
        json.put("SID", tran.getSessionID());
        json.put("MSG_TYPE", WebConnection.MessageTypes.CMD_RSPONSE);
        json.put("MTYPE", tran.getTransmission().getmType().name());
        //TODO failsafe for if tran.moduleIDS.length = 0
        List<String> cmd = new ArrayList<>();
        cmd.add(tran.getTransmission().getcType().name());
        cmd.addAll(Arrays.asList(tran.getTransmission().getMsg().toUpperCase().split(",")));
        json.put("CMD", cmd); //ex. { "CMD" : ["SET", "R_DLY", "3000"]}

        Integer[] modList;
        if (tran.isBroadcast())
            modList = tran.getResponses().keySet().toArray(new Integer[] {});
        else
            modList = tran.getModuleIDs();
        JSONObject ModuleListJSON = new JSONObject();
        for (Integer mid : modList){
            Module m = getHub().getModule(mid);
            if (m == null)//TODO unnecessary?
                continue;
            
            JSONObject jsonMod = new JSONObject();
            jsonMod.put("ID", m.getmID());
            if (m.getAlias() != null && m.getAlias().length() > 0)
                jsonMod.put("Alias", m.getAlias());
            boolean success = tran.isBroadcast() || Arrays.asList(tran.getFinishedModuleIDs()).contains(m.getmID());
            jsonMod.put("Success", success);
            if (success)
                jsonMod.put("Response", tran.getResponses().get(m.getmID()));
            
            ModuleListJSON.put(m.getmID(), jsonMod);
        }
        //TODO implement detailed results. Response from each module in a table
        json.put("Modules", ModuleListJSON);
        getHub().getWebConnection().writeToWeb(json.toString());
    }
    
}
