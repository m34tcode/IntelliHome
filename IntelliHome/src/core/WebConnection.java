/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.json.simple.JSONObject;

/**
 *
 * @author Josh_2
 */
public class WebConnection {
    
    public enum TheFile {
        WISO, WOSI, ModuleList;
    }
    
    public enum MessageTypes {
        CMD_RSPONSE,CMD_ERROR,MOD_LIST;
    }
    
    private final HashMap<String, Date> activeSessions = new HashMap<>();
    private final File moduleListFile = new File("C:\\Users\\Josh_2\\Documents\\HAS-files\\modlist.txt");
    private final File WOSIFile = new File("C:\\Users\\Josh_2\\Documents\\HAS-files\\WOSI.txt");
    private final File WISOFile = new File("C:\\Users\\Josh_2\\Documents\\HAS-files\\WISO.txt");
    private final Timer IOMonitorTimer = new Timer();
    private final List<String> WISOQueue = new ArrayList<>();
    private final NRFHub hub;
    
    private String lastModListJSONString = "";
    
    public WebConnection(NRFHub hub) {
        this.hub = hub;
        IOMonitorTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                checkWISOQueue();
                checkWOSI();
            }
        }, 1000, 1000);
    }
    
    private synchronized void checkWISOQueue(){
        for (String str : WISOQueue)
            writeToFile(WISOFile, str);
    }
    
    private synchronized void checkWOSI(){
        String WOSICommands = readFromFile(WOSIFile);
        Matcher m = Pattern.compile("(?<SID>\\w*?):(?<CMD>.*)\n?").matcher(WOSICommands);
        
        while (m.find()){
            hub.execute(m.group());
        }
    }
    
    public void writeToWeb(String msg){
        writeToFile(WISOFile, msg + "\n");
        System.out.println("Wrote to webclient: \"" + msg + "\"");
    }
    
    private synchronized String readFromFile(File f) {
        StringBuilder result = new StringBuilder();
        try {
            FileChannel chan = new RandomAccessFile(f, "rw").getChannel();
            FileLock lock = chan.lock();
            try {
                ByteBuffer buf = ByteBuffer.allocate(1024);
                int bytesRead = chan.read(buf);
                while (bytesRead != -1){
                    buf.flip();
                    while (buf.hasRemaining()){
                        result.append((char)buf.get());
                    }

                    buf.clear();
                    bytesRead = chan.read(buf);
                }
                
                chan.truncate(0);
            } finally {
                lock.release();
                chan.close();
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(WebConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(WebConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        if (result.toString().length() > 0)
            System.out.println("Read command from server: \"" + result.toString().trim() + "\"");
        return result.toString();
    }
    
    private synchronized boolean writeToFile(File f, String toWrite){
        StringBuilder result = new StringBuilder();
        try {
            FileChannel chan = new FileOutputStream(f, true).getChannel();
            FileLock lock = chan.lock();
            try {
                ByteBuffer buf = ByteBuffer.allocate(toWrite.length());
                buf.put(toWrite.getBytes());
                
                buf.flip();
                
                while (buf.hasRemaining()){
                    chan.write(buf);
                }
            } finally {
                lock.release();
                chan.close();
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(WebConnection.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            Logger.getLogger(WebConnection.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        return true;
    }
    
    public JSONObject getTransactionModuleListJSON(Transaction tran){
        JSONObject json = new JSONObject();
        Integer[] modList;
        if (tran.isBroadcast())
            modList = tran.getResponses().keySet().toArray(new Integer[] {});
        else
            modList = tran.getModuleIDs();
        
        for (Integer mid : modList){
            Module m = hub.getModule(mid);
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
            
            json.put(m.getmID(), jsonMod);
        }
        
        return json;
    }
    
    public void writeCommandResultToWeb(String SSID, ModuleType mType, String cmd, JSONObject modListJSON){
        JSONObject json = new JSONObject();
        json.put("SSID", SSID);
        json.put("MSG_TYPE", MessageTypes.CMD_RSPONSE);
        json.put("MTYPE", mType);
        json.put("CMD", cmd);
        json.put("MODULES", modListJSON);
        writeToWeb(json.toString());
    }
    
    public void writeCommandErrorToWeb(String SSID, ModuleType mType, String cmd, List<Module> modules, String errorMessage){
        JSONObject json = new JSONObject();
        json.put("SSID", SSID);
        json.put("MSG_TYPE", MessageTypes.CMD_ERROR);
        if (errorMessage != null && errorMessage.length() > 0)
            json.put("ERROR_MESSAGE", errorMessage);
        json.put("MTYPE", mType);
        json.put("CMD", cmd);
        
        if (modules != null){
            JSONObject modJSON = new JSONObject();
            for (Module m : modules){
                if (m == null)
                    continue;
                JSONObject jsonMod = new JSONObject();
                jsonMod.put("ID", m.getmID());
                jsonMod.put("Token", m.getToken());
                if (m.getAlias() != null && m.getAlias().length() > 0)
                    jsonMod.put("Alias", m.getAlias());
                modJSON.put(m.getmID(), jsonMod);
            }
            json.put("MODULES",modJSON);
        }
        
        writeToWeb(json.toString());
    }
    
    public void writeModuleList(List<Module> modules){
        JSONObject json = new JSONObject();
        for (Module m : modules){
            if (m == null)
                continue;
            JSONObject jsonMod = new JSONObject();
            jsonMod.put("ID", m.getmID());
            jsonMod.put("Token", m.getToken());
            jsonMod.put("Type", m.getmType());
            jsonMod.put("Active", m.isActive());
            jsonMod.put("Last Response", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(m.getLastResponseTime()));
            if (m.getAlias() != null && m.getAlias().length() > 0)
                jsonMod.put("Alias", m.getAlias());
            json.put(m.getmID(), jsonMod);
        }
        
        if (!json.toString().equals(lastModListJSONString)){
            writeToFile(moduleListFile, json.toString());
        }
    }
}
