/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

import java.util.Random;

/**
 *
 * @author Josh
 */
public class GLOBAL {
    //Data format: D;<ID>;<MOD_TYPE>;<DATA_STRING>
    //                ID 1-3 chars, MTYPE 1-8 chars, DATA limitied by message length
    public static String SERIAL_DATA_PATTERN = "D;(?<ID>\\d{1,3});(?<MTYPE>\\w{1,8});(?<TID>\\d{1,3});(?<DATA>.*)";
    public static String SERIAL_NOTIFICATION_PATTERN = "N;(?<ID>\\d{1,3});(?<MTYPE>\\w{1,8});(?<NTYPE>\\w{1,8});(?<MSG>.*)";
    public static String SERIAL_PATTERN = "(?<ID>\\d{1,3});(?<MTYPE>\\w{1,8});(?<TID>\\w{1,8});(?<CTYPE>\\w{1,4});(?<MSG>.*)";

    public static int NRF_MAX_MSG_LEN = 28;
    public static int NRF_MAX_RETRIES = 2;
    public static int TID_LENGTH = 3;
    public static int TRANSACTION_TIMEOUT = 4000;
    
    public static final Random rand = new Random(System.currentTimeMillis());
    public static int MODULE_ALIVE_TIMEOUT = 120000;
}
