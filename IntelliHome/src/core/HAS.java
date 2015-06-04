package core;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author Josh
 */
public class HAS {

    
    /*
    *  TODO
    *    implement combined modules
    */
    /**
     * @param args the command line arguments
     */
    static SerialInterface si;
    static NRFHub n;
    static DHTManager dhtm;
    static SwitchManager swm;
    static WebConnection webConn;
    
    public static void main(String[] args) throws InterruptedException {
        try {
            si = new SerialInterface();
        } catch (IOException ex) {
            Logger.getLogger(HAS.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
        
        n = new NRFHub();
        n.SetSerialInterface(si);
        
        dhtm = new DHTManager();
        dhtm.setHub(n);
        swm = new SwitchManager();
        swm.setHub(n);
        
        n.registerManager(ModuleType.DHT, dhtm);
        n.registerManager(ModuleType.SW, swm);
        n.registerManager(ModuleType.SW2, swm);
        
        webConn = new WebConnection(n);
        n.setWebConnection(webConn);
        
        SerialTransmission tran = new SerialTransmission(SerialTransmission.DIRECTION.OUTGOING, 255, ModuleType.DHT, CommandType.COMMAND, "SETUP");
        n.transmit(tran, true);
        
        ModuleManagerJFrame manager = new ModuleManagerJFrame(n);  
        manager.setVisible(true);
    }
    
}
