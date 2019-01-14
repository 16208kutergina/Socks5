package com.company;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

class PortForwarder {
    private StartMessageMenager startMessageMenager = new StartMessageMenager();
    private Selector selector = null;

    void start(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocket;
        serverSocket = ServerSocketChannel.open();
        assert serverSocket != null;
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            while (iter.hasNext()) {

                SelectionKey key = iter.next();
                Attachment attachment = (Attachment) key.attachment();
                if (key.isValid() && key.isAcceptable()) {
                    System.out.println("accept");
                    accept(key);
                }
                if (key.isValid() && key.isConnectable()) {
                    System.out.println("connect");
                    connect(key);
                }
                if (key.isValid() && key.isReadable()) {
                    System.out.println("read");
                    read(key);
                }
                if (key.isValid() && key.isWritable()) {
                    System.out.println("write");
                    write(key);
                }
                iter.remove();
            }

        }
    }

    private void accept(SelectionKey key){
        SocketChannel clientChannel = null;
        // SocketChannel newHostChannel = null;
        try {
            clientChannel = ((ServerSocketChannel)key.channel()).accept();
            clientChannel.configureBlocking(false);
            // newHostChannel = SocketChannel.open();
            // newHostChannel.configureBlocking(false);
            // newHostChannel.connect(new InetSocketAddress(rhost,rport));
            Attachment client = new Attachment(clientChannel, selector);
            // Attachment newHost = new Attachment(newHostChannel,selector);
            //  client.setOtherAttachment(newHost);
            //  newHost.setOtherAttachment(client);
            //  newHostChannel.register(selector, SelectionKey.OP_CONNECT, newHost);
            clientChannel.register(selector,SelectionKey.OP_READ, client);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                assert clientChannel != null;
                clientChannel.close();
                //  assert newHostChannel != null;
                //    newHostChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void connect(SelectionKey key){
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        try {
            channel.finishConnect();
//
            attachment.deleteOption(SelectionKey.OP_CONNECT);
            attachment.addOption(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
         attachment.getOtherAttachment().addOption(SelectionKey.OP_READ  );
            //System.out.println(attachment.whoIam+" :\n" + "Accept " + key.isAcceptable()+"\n Connect " + key.isConnectable()+"\nRead " + key.isReadable() + "\nWrite " + key.isWritable()+"\n");
          //  System.out.println(attachment.getOtherAttachment().whoIam+":\n" + "Accept " + key.isAcceptable()+"\n Connect " + key.isConnectable()+"\nRead " + key.isReadable() + "\nWrite " + key.isWritable()+"\n");

        } catch (IOException e) {
            e.printStackTrace();
            attachment.close();
        }
    }

    private void read(SelectionKey key){
        Attachment attachment = (Attachment) key.attachment();
        try {
            int byteRead = attachment.getSocketChannel().read(attachment.getBuf());
            //System.out.println(attachment.whoIam +" read :\n"+ Arrays.toString(attachment.getBuf().array()));
            //System.out.println(attachment.whoIam+" read:\n" + "Accept " + key.isAcceptable()+"\n Connect " + key.isConnectable()+"\nRead " + key.isReadable() + "\nWrite " + key.isWritable()+"\n");
             //System.out.println(attachment.whoIam +": "+Arrays.toString(attachment.getBuf().array()));
            if (attachment.getOtherAttachment() == null) {
                if(attachment.isFirstMessage()){
                    startMessageMenager.greetingMessage(attachment);
                }else{
                    startMessageMenager.headersMessage(attachment, byteRead, selector);
                }
            } else {
               // int byteRead = attachment.getSocketChannel().read(attachment.getBuf());
                if (byteRead > 0) {
                    attachment.getOtherAttachment().addOption(SelectionKey.OP_WRITE);
                } else if (byteRead == -1) {
                    attachment.deleteOption(SelectionKey.OP_READ);
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
            attachment.close();
        }

    }

    private void write(SelectionKey key){
        Attachment attachment = (Attachment) key.attachment();
        attachment.getOtherAttachment().getBuf().flip();
        try {
            int byteWrite = attachment.getSocketChannel().write(attachment.getOtherAttachment().getBuf());
            if (byteWrite > 0) {
                attachment.getOtherAttachment().getBuf().compact();
                attachment.getOtherAttachment().addOption(SelectionKey.OP_READ);
            }
            if (attachment.getOtherAttachment().getBuf().position() == 0) {
                attachment.deleteOption(SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
