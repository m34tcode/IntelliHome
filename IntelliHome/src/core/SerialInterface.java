/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import gnu.io.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Timer;

/**
 * This class communicates directly with the hub.
 * @author Josh
 */
public class SerialInterface implements ActionListener {

    private BufferedInputStream input;
    private OutputStream output;
    private Timer serialObserver;
    
    private SerialPort serialPort = null;
    private Enumeration ports;
    private final String[] portNames = {};
    private boolean portOpen = true; //TODO implement/why?? //True if the hub is connected and the port is opened
    
    long startMillis = System.currentTimeMillis();
    final static int TIMEOUT = 2000;
    private final List<FailSendEventListener> FailSendEventListeners = new ArrayList<>();
    
    static SerialInterface si;

    /**
     * debugging purposes only
     * @param args currently unused
     */
    public static void main(String[] args) {
        try {
            si = new SerialInterface();
        
        
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                Logger.getLogger(SerialInterface.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            si.transmit(new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.COMMAND, "HALT"));
                
            while(true) {
                si.transmit(new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.GET, "ID"));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(SerialInterface.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(SerialInterface.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private final int NRF_BROADCAST_ADDRESS = 255;
    private final List<SerialObjectListener> serialListeners = new ArrayList<>();
    
    /**
     * Creates a new Serial interface
     * @throws IOException
     */
    public SerialInterface() throws IOException{
        if (!Connect())
            throw new IOException("Failed to connect to the hub");
        
        serialObserver = new Timer(100, this); //TODO why does decreasing the iteration delay cause missed characters?
        serialObserver.start();
    }
    
    private void flushInput(){
        try {
            while (input != null && input.available() > 0)
                input.read();
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            if (input != null && input.available() > 0)
                serialEvent();
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private StringBuilder msg = new StringBuilder();

    /**
     * 
     */
    private void serialEvent() {
        char c;
        try {
            while (input.available() > 0){
                c = (char)input.read();
                if (c == '\n'){
                    //if (!msg.toString().matches("\\s*")) { dataRecieved(msg.toString()); }
                    dataRecieved(msg.toString());
                    msg = new StringBuilder();
                } else {
                    msg.append(c);
                }
            }
        } catch (Exception ex){
            ex.printStackTrace();
            msg = new StringBuilder();
        }
    }
    
    
    //find and connect to NRF Hub
    private boolean Connect(){
        CommPortIdentifier comSerId;
        try {
            //try each named port
            for (String portName : portNames){
                comSerId = CommPortIdentifier.getPortIdentifier(portName);
                //open the port
                serialPort = (SerialPort)comSerId.open("SerialInterface",TIMEOUT);
                input = new BufferedInputStream(serialPort.getInputStream());
                output = serialPort.getOutputStream();
                //if this port was valid, attempt to initialize it
                if (serialPort != null
                        && InitializeHub(serialPort))
                    return true;
                //Failed to initialize the port, so close it and move on
                serialPort.close();
            }
        } catch (NoSuchPortException | PortInUseException | IOException ex) {
            System.out.println("Failed to init port: " + ex.getMessage());
        }
        //begin testing all connected ports, since the named ports all failed
        ports = CommPortIdentifier.getPortIdentifiers();
        System.out.println("going through ports");
        while (ports.hasMoreElements()){//TODO cleanup innards of this loop
            comSerId = (CommPortIdentifier)ports.nextElement();
            System.out.println("opening: " + comSerId.getName());
            //if a port is open from the previous loop iteration, close it
            if (serialPort != null) //close the previously opened port, which wasnt the correct port
                serialPort.close();
            try {
                //Attempt to open the port
                serialPort = (SerialPort)comSerId.open("SerialInterface",TIMEOUT);
                input = new BufferedInputStream(serialPort.getInputStream());
                output = serialPort.getOutputStream();
                //wait for some reason - debugging purposes
                Thread.sleep(300);
            } catch (PortInUseException ex) {
                System.out.println("Port in use: " + comSerId.getName());
                continue;
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(SerialInterface.class.getName()).log(Level.SEVERE, null, ex);
                continue;
            }
            //Attempt to initialize this port
            System.out.println("attempting init of " + serialPort.getName());
            if (InitializeHub(serialPort))
                return true;
        }
        return false;
    }
   
    //Restart hub and make sure it inits succesfully, else return false
    private boolean InitializeHub(SerialPort serialPort) {
        //Reset the device connected to this port
        serialPort.setDTR(false);
        try {
            Thread.sleep(25);
        } catch (InterruptedException ex) {
            return false;//TODO differentiate this from failed init on correct port (HI;0)
        }
        serialPort.setDTR(true);
        //prepare for intitialization message from hub
        StringBuilder sb = new StringBuilder();
        long sMillis = System.currentTimeMillis();
        
        //loop through data, storing it until an init message is found
        while (sMillis + 5000 > System.currentTimeMillis()){
            try {
                if (input.available() > 0){
                    char c = (char)input.read();
                    sb.append(c);
                    System.out.print(c);
                }
                if (sb.toString().contains("HI;1")){
                    System.out.println("Found hub on " + serialPort.getName());
                    return true;
                } else if (sb.toString().contains("HI;0")){
                    return false;//TODO modify code to diferentiate between failed init, and wrong port. Maybe exception here?
                }
            } catch (IOException ex) {
                System.out.println("");
                Logger.getLogger(SerialInterface.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        return false;
    }
    
    //Send a message to the hub over this serial connection

    /**
     * Sends a prepared transmission the the hub
     * @param tran Transmission to send
     * @return true if the transmission process was completed. This does not guarantee that the transmission was succesful.
     */
    public synchronized boolean transmit(SerialTransmission tran){
        return transmit(tran,new Integer[] {tran.getmID()});
    }
    
    /**
     * Sends a prepared transmission the the hub
     * @param sTran Transmission to send
     * @param IDs The modules to transmit to
     * @return true if the transmission process was completed. This does not garauntee that the transmission was succesful.
     */
    
    public synchronized boolean transmit(SerialTransmission sTran, Integer[] IDs){
        SerialTransmission tran;
        for (Integer ID : IDs)
            if (portOpen)
                try {
                    tran = sTran.clone(ID);
                    Thread.sleep(5);
                    System.out.println("Sending Serial: " + tran.serialize());
                    String s = (tran.serialize() + "\r\n");
                    //arrPrint(s.getBytes());
                    output.write(s.getBytes());
                    output.flush();
                    //return true;
                } catch (Exception ex){
                    ex.printStackTrace();
                    return false;
                }
        return true;
    }
    
    public synchronized boolean broadcast(SerialTransmission transmission){
        transmission.setmID(NRF_BROADCAST_ADDRESS);
        return transmit(transmission, new Integer[] {NRF_BROADCAST_ADDRESS});
    }
    
//    /**
//     *
//     * @param recipients module to send this message to
//     * @param msg
//     * @return
//     */
//        public boolean transmit(Integer[] recipients, String msg){
//        for (int recipient : recipients){
//            if (portOpen)
//                try {
//                    msg = "" + recipient + ";" + msg + "\n"; //TODO add message id?
//                    output.write(msg.getBytes());
//                    output.flush();
//                    System.out.println(msg);
//                } catch (IOException e){
//                    return false; //TODO figure why we recieved IO exception
//                }
//        }
//        return true;//TODO is this necessary?
//    }
    
    //Temporary
    //TODO replace with listeners

    private SerialData parseData(String data){
        Matcher m = Pattern.compile(GLOBAL.SERIAL_DATA_PATTERN).matcher(data);
        if (!m.find())
            return null;
        
        int mID = Integer.parseInt(m.group("ID"));
        ModuleType mType = ModuleType.fromShortCode(m.group("MTYPE"));
        String dataString = m.group("DATA");
        
        if (mType == null || dataString == null)
            return null;
        
        SerialData result = new SerialData(mID, m.group("TID"), mType, m.group("DATA"));
        return result;
    }

    private SerialNotification parseNotification(String notification) {
        Matcher match = Pattern.compile(GLOBAL.SERIAL_NOTIFICATION_PATTERN).matcher(notification);
        if (!match.find())
            return null;
        
        SerialNotification.Notifications nType = null;
        ModuleType mType = null;
        Integer mID = 0;
        try {
            nType = SerialNotification.Notifications.fromShortCode(match.group("NTYPE"));
            mType = ModuleType.valueOf(match.group("MTYPE"));
            mID = Integer.parseInt(match.group("ID"));
        } catch (Exception e){
            e.printStackTrace();
        }
        
        if (mID == 0 || mType == null)
            return null;
        
        return new SerialNotification(mID, mType, nType, match.group("MSG"));
    }

    private SerialTransmission parseSerial(String str) {
        Matcher m = Pattern.compile(GLOBAL.SERIAL_PATTERN).matcher(str);
        
        if (!m.find())
            return null;
        
        Integer id = Integer.parseInt(m.group("ID"));
        ModuleType mType = ModuleType.fromShortCode(m.group("MTYPE"));
        CommandType cType = CommandType.fromShortCode(m.group("CTYPE"));
        String TID = m.group("TID");
        
        if (id != 0 && mType != null && cType != null && TID != null && TID.length() > 0){
            return new SerialTransmission(SerialTransmission.DIRECTION.INCOMING, TID, id, mType, cType, m.group("MSG"));
        } else
            return null;
    }
    
    /**
     *
     * @param str
     */
    private void dataRecieved(String str) {
        System.out.println("Recieved Serial: " + str);
        if (str.startsWith("TXFail:")){
            Matcher m = Pattern.compile("TXFail:TID:(\\w*):MID:(\\d)").matcher(str);
            if (m.find()){
                onFailSend(m.group(1),Integer.parseInt(m.group(2)));
            }
        } else if (str.startsWith("D;")){ //true for data
            //System.out.println("Parsing Serial Data Packet");
            for (SerialObjectListener listener : serialListeners) {
                SerialData parsed = parseData(str);
                if (parsed != null) {
                    listener.serialDataRecieved(parsed);
                }
            }
        } else if (str.startsWith("N;")){ //true for notifications
            for (SerialObjectListener listener : serialListeners) {
                SerialNotification parsed = parseNotification(str);
                if (parsed != null) {
                    listener.serialNotificationRecieved(parsed);
                }
            }
        } else {
            for (SerialObjectListener listener : serialListeners) {
                SerialTransmission parsed = parseSerial(str);
                if (parsed != null) {
                    listener.serialTransmissionRecieved(parsed);
                }
            }
        }
    }
    
    public void addFailSendEventListener(FailSendEventListener l){
        FailSendEventListeners.add(l);
    };
    
    public void removeFailSendEventListener(FailSendEventListener l){
        FailSendEventListeners.remove(l);
    };
    
    public void onFailSend(String TID, int MID){
        for (FailSendEventListener listener : FailSendEventListeners){
            listener.onFailSend(TID, MID);
        }
    }
    /**
     *
     * @param l the listener to subscribe
     */
    public void serialSubscribe(SerialObjectListener l){
        this.serialListeners.add(l);
    }
    
    /**
     *
     * @param l the listener to subscribe
     */
    public void serialUnsubscribe(SerialObjectListener l){
        this.serialListeners.remove(l);
    }
}
