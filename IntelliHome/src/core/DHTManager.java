/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.simple.JSONObject;

/**
 *
 * @author Josh
 */
public class DHTManager implements Manager, TransactionEventListener {

    private Connection con;
    private Statement st;
    private ResultSet rs;
    private NRFHub hub;
    
    private int responseTimeout = 10000;
    private final HashMap<Integer,HashMap<String,java.util.Date>> recentTIDs = new HashMap<>();
    private List<Module> DHTs = new ArrayList<>();
    private Timer responsetimer = new Timer(true);
    
    public DHTManager() {
        try {
            String url = "jdbc:mysql://localhost:3306/climate";
            Class.forName("com.mysql.jdbc.Driver");
            con = DriverManager.getConnection(url, "root", "birthday");
            st = con.createStatement();
        } catch (SQLException ex) {
            Logger.getLogger(DHTManager.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(DHTManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        responsetimer.schedule(new TimerTask() {

            @Override
            public void run() {
                // DHTManager field: private final HashMap<Integer,HashMap<String,java.util.Date>> recentTIDs = new HashMap<>();
                synchronized (this){
                    HashMap<String, java.util.Date> TIDs;
                    for (Integer mID : recentTIDs.keySet()){
                        if ((TIDs = recentTIDs.get(mID)) != null){
                            for (String TID : TIDs.keySet().toArray(new String[]{})){ //Line 52
                                if (TIDs.get(TID) != null){
                                    if (Date.from(Instant.now()).getTime() - TIDs.get(TID).getTime() > responseTimeout) {
                                        TIDs.remove(TID);
                                    }
                                }
                            }
                        } else {
                            recentTIDs.put(mID,new HashMap<String, java.util.Date>());
                        }
                    }
                }
            }
        }, 2000, 2000);
    }
    
    @Override
    public boolean handles(ModuleType mType) {
        return mType.equals(ModuleType.DHT);
    }

    @Override
    public void handle(SerialData data) {
        hub.transmit(new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING,
                data.getmID(), data.getmType(), CommandType.DATA_RESPONSE, data.getTID()), false);
        
        synchronized (recentTIDs){
            if (!recentTIDs.containsKey(data.getmID())){
                recentTIDs.put(data.getmID(),new HashMap<String, java.util.Date>());
            }
            if (!recentTIDs.get(data.getmID()).containsKey(data.getTID())) {
                recentTIDs.get(data.getmID()).put(data.getTID(), java.util.Date.from(Instant.now()));
            } else {
                return;//because this data TID for this module has been recieved already
            }
        }
        
        try {
            Matcher m = Pattern.compile("(?:(?:T,(?<TEMP>\\d*);?)|(?:H,(?<HUMIDITY>\\d*);?)){2}").matcher(data.getData());
            
            if (m.find()){
                System.out.println("Inserting data from ID: " + data.getmID() + "  values: TEMP: " + (Integer.valueOf(m.group("TEMP")) / 100.0) + "; HUM: " + (Integer.valueOf(m.group("HUMIDITY")) / 100.0));
                st.executeUpdate("INSERT INTO climate(Time, Temp, Humidity) VALUES(NOW(), " + (Integer.valueOf(m.group("TEMP")) / 100.0) + ", " + (Integer.valueOf(m.group("HUMIDITY")) / 100.0) + ")");
            }
        } catch (SQLException ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void handle(SerialNotification notification) {
        //TODO implement
    }

    @Override
    public void handle(SerialTransmission tran) {
        //TODO Implement
    }

    @Override
    public boolean execute(String command) {//TODO implement get read delay/count
        if (getHub() == null)
            return false;
        
        Matcher DHTMatcher = Pattern.compile("(?<SSID>[-,a-zA-Z0-9]{1,128}):DHT\\.(?:Modules?\\[(?<modules>(?:\\w*,?)*)\\]|(?<allModules>AllModules))\\.(?<command>\\w*)(?:\\[(?<commandParam>\\w*)\\])?").matcher(command);
        List<Module> targetModules = new ArrayList<>();
        
        if (DHTMatcher.find()){
            if (DHTMatcher.group("allModules") != null)
                targetModules.addAll(DHTs);
            else {
                targetModules.addAll(hub.getModules(DHTMatcher.group("modules").split(","), NRFHub.ModuleInitFilter.INITIALIZED_MODULES));
                for (Module m : targetModules){
                    if (m == null || m.getmType() != ModuleType.DHT)//null check 'should' be unnecessary
                        targetModules.remove(m);
                }
            }
            
            String tranMsg;
            TransactionResponseListener trl;
            switch (DHTMatcher.group("command")){
                case "SetReadDelay":
                    tranMsg = "R_DLY," + DHTMatcher.group("commandParam");
                    trl = new TransactionResponseListener() {

                        @Override
                        public void onResponseSet(Transaction tran, Integer modID, String response) {
                            if (response != null && !response.matches("R_DLY,\\d{1,7}")){
                                tran.setResponse(modID, null);
                                System.out.println("nullified response from module " + modID + ", for command SetReadDELAY: " + response);
                            }
                        }
                    };
                    break;
                case "SetReadCount":
                    tranMsg = "R_CNT," + DHTMatcher.group("commandParam");
                    trl = new TransactionResponseListener() {

                        @Override
                        public void onResponseSet(Transaction tran, Integer modID, String response) {
                            if (response != null && !response.matches("R_CNT,\\d{1,7}")){
                                tran.setResponse(modID, null);
                                System.out.println("nullified response from module " + modID + ", for command SetReadCount: " + response);
                            }
                        }
                    };
                    break;
                default:
                    return false;
            }
            SerialTransmission sTran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.SET, tranMsg);
            Matcher m = Pattern.compile("(?<SID>\\w*?):DHT\\.AllModules\\.SetReadDelay\\[(?<RDLY>\\d*)\\]").matcher(command);
            Transaction t = getHub().transmit(sTran, true);
            t.addTransactionEventListener(this);
            t.setSessionID(DHTMatcher.group("SSID"));
            t.addTransactionResponseListener(trl);
            return true;
        } else {
            return false;
        }
        
        
        //<editor-fold defaultstate="collapsed" desc="Old code for reference">
        /*
if (m.find()){
            String tranMsg = "R_DLY," + m.group("RDLY");
            SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.SET, tranMsg);
            Transaction t = getHub().transmit(tran, true);
            t.addTransactionEventListener(this);
            t.setSessionID(m.group("SID"));
            t.addTransactionResponseListener(new TransactionResponseListener() {

                @Override
                public void onResponseSet(Transaction tran, Integer modID, String response) {
                    if (response != null && !response.matches("R_DLY,\\d{1,7}")){
                        tran.setResponse(modID, null);
                        System.out.println("nullified response from module " + modID + ", for command SetAll.ReadDelay: " + response);
                    }
                }
            });
            return true;
        }
        
        m = Pattern.compile("(?<SID>\\w*?):DHT\\.AllModules\\.SetReadCount\\[(?<RCNT>\\d*)\\]").matcher(command);
        if (m.find()){
            String tranMsg = "R_CNT," + m.group("RCNT");
            SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.SET, tranMsg);
            Transaction t = getHub().transmit(tran, true);
            t.addTransactionEventListener(this);
            t.setSessionID(m.group("SID"));
            t.addTransactionResponseListener();
            return true;
        }
        
        m = Pattern.compile("(?<SID>\\w*?):DHT\\.Module\\[(?<MOD>\\w*?)\\]\\.SetReadCount\\[(?<RCNT>\\d*)\\]").matcher(command);
        if (m.find()){
            String tranMsg = "R_CNT," + m.group("RCNT");
            Module module = getHub().getModule(m.group("MOD"));
            if (module != null){
                SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, module.getmID(), ModuleType.DHT, CommandType.SET, tranMsg);
                Transaction t = getHub().transmit(tran, true);
                t.addTransactionEventListener(this);
                t.setSessionID(m.group("SID"));
            t.addTransactionResponseListener(new TransactionResponseListener() {

                @Override
                public void onResponseSet(Transaction tran, Integer modID, String response) {
                    if (response != null && !response.matches("R_CNT,\\d{1,7}")){
                        tran.setResponse(modID, null);
                        System.out.println("nullified response from module " + modID + ", for command SetReadCount: " + response);
                    }
                }
            });
                return true;
            } else
                return false;
        }
        
        m = Pattern.compile("(?<SID>\\w*?):DHT\\.Module\\[(?<MOD>\\w*?)\\]\\.SetReadDelay\\[(?<RDLY>\\d*)\\]").matcher(command);
        if (m.find()){
            String tranMsg = "R_DLY," + m.group("RDLY");
            Module module = getHub().getModule(m.group("MOD"));
            if (module != null){
                SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, module.getmID(), ModuleType.DHT, CommandType.SET, tranMsg);
                Transaction t = getHub().transmit(tran, true);
                t.addTransactionEventListener(this);
                t.setSessionID(m.group("SID"));
                t.addTransactionResponseListener(new TransactionResponseListener() {

                    @Override
                    public void onResponseSet(Transaction tran, Integer modID, String response) {
                        if (response != null && !response.matches("R_DLY,\\d{1,7}")){
                            tran.setResponse(modID, null);
                            System.out.println("nullified response from module " + modID + ", for command SetReadDelay: " + response);
                        }
                    }
                });
                return true;
            } else
                return false;
        }
        
        
        return false;*/
//</editor-fold>
    }

    @Override
    public void updateModuleList(List<Module> modules) {
        DHTs.clear();
        for (Module m : modules) 
            if (handles(ModuleType.SW2))
                DHTs.add(m);
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
        json.put("SSID", tran.getSessionID());
        json.put("MSG_TYPE", WebConnection.MessageTypes.CMD_RSPONSE);
        json.put("MTYPE", tran.getTransmission().getmType().name());
        json.put("CMD", tran.getTransmission().getMsg());
        JSONObject ModuleListJSON = getHub().getWebConnection().getTransactionModuleListJSON(tran);
        json.put("MODULES", ModuleListJSON);
        getHub().getWebConnection().writeToWeb(json.toString());
    }
}
