package com.company;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Main {

    public static void main(String[] args) throws IOException {
        if(args.length != 1){
            System.out.println("Give me port");
            return;
        }

        int port;


        try{
             port = Integer.parseInt(args[0]);
            PortForwarder portForwarder = new PortForwarder();
            portForwarder.start(port);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Incorrect data");
        }

    }
}
