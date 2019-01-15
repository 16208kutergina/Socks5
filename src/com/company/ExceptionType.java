package com.company;

public class ExceptionType {
    public static byte success = 0x00;
    final public static byte exceptionSocksServer = 0x01;
    public static byte connectionBanned = 0x02;
    public static byte networkNotAvailable = 0x03;
    public static byte hostNotAvailable = 0x04;
    public static byte connectionRefused = 0x05;
    public static byte expiryTTL = 0x06;
    public static byte commandNotSupported = 0x07;
    public static byte addressTypeNotSupported = 0x08;
    public static byte notDefined = 0x09;


}
