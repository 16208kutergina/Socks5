package com.company;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class Attachment {

    Attachment getOtherAttachment() {
        return otherAttachment;
    }

    ByteBuffer getBuf() {
        return buf;
    }

    SocketChannel getSocketChannel() {
        return socketChannel;
    }

    boolean isFinishRead() {
        return isFinishRead;
    }

    void setOtherAttachment(Attachment otherAttachment) {
        this.otherAttachment = otherAttachment;
    }


    public void setFinishRead(boolean finishRead) {
        isFinishRead = finishRead;
    }


    public void setFinishWrite(boolean outputShutdown) {
        this.finishWrite = outputShutdown;
    }

    private Selector selector;
    private Attachment otherAttachment;
    private int bufferSize = 4096;
    private ByteBuffer buf = ByteBuffer.allocate(bufferSize);
    private SocketChannel socketChannel;
    private boolean firstMessage = true;
    public String whoIam;
    private int port;

    public InetAddress getHost() {
        return host;
    }

    public void setHost(InetAddress host) {
        this.host = host;
    }

    private InetAddress host;


    public boolean isFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(boolean firstMessage) {
        this.firstMessage = firstMessage;
    }



    public boolean isFinishWrite() {
        return finishWrite;
    }

    private boolean finishWrite = false;
private boolean isFinishRead = false;

    Attachment(SocketChannel socketChannel, Selector selector, String whoIam){
        this.socketChannel = socketChannel;
        this.selector = selector;
        this.whoIam = whoIam;
    }


    void close() {
        try {
            socketChannel.close();
            socketChannel.keyFor(selector).cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void addOpcion(int opcion){
        SelectionKey currentOption = socketChannel.keyFor(selector);
        currentOption.interestOps(currentOption.interestOps()|opcion);
    }

    void deleteOpcion(int opcion){
        SelectionKey currentOption = socketChannel.keyFor(selector);
        currentOption.interestOps(currentOption.interestOps()&~opcion);
    }


    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}





