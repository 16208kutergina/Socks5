package com.company;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class Attachment {
    private Selector selector;
    private Attachment otherAttachment;
    private int bufferSize = 8*1024;
    private ByteBuffer buf = ByteBuffer.allocate(bufferSize);
    private SocketChannel socketChannel;
    private boolean firstMessage = true;


    public boolean isFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(boolean firstMessage) {
        this.firstMessage = firstMessage;
    }

    Attachment(SocketChannel socketChannel, Selector selector){
        this.socketChannel = socketChannel;
        this.selector = selector;
    }

    public Attachment getOtherAttachment() {
        return otherAttachment;
    }

    public ByteBuffer getBuf() {
        return buf;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setOtherAttachment(Attachment otherAttachment) {
        this.otherAttachment = otherAttachment;
    }

    public void close() {
        try {
            socketChannel.close();
            socketChannel.keyFor(selector).cancel();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addOption(int option) {
        SelectionKey currentOption = socketChannel.keyFor(selector);
        currentOption.interestOps(currentOption.interestOps() | option);
    }

    public void deleteOption(int option) {
        SelectionKey currentOption = socketChannel.keyFor(selector);
        currentOption.interestOps(currentOption.interestOps() & ~option);
    }
}





