package com.company;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

class PortForwarder {
    private StartMessageMenager startMessageMenager = new StartMessageMenager();
    private Selector selector = null;
   static DatagramChannel dnsChannel;
   static HashMap<Integer, SelectionKey> dnsMap = new HashMap<>();
    private String dnsServer = ResolverConfig.getCurrentConfig().server();

    void start(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocket;
        serverSocket = ServerSocketChannel.open();
        assert serverSocket != null;
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        dnsChannel.connect(new InetSocketAddress(dnsServer, 53));
        SelectionKey DNSKey = dnsChannel.register(selector, SelectionKey.OP_READ);

        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                if(DNSKey == key) {
                    if (key.isReadable()) {
                        System.out.println("dns");
                        resolveDns();
                    }
                    }else {
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
                }
                iter.remove();
            }

        }
    }

    private void resolveDns() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        if (dnsChannel.read(buf) <= 0)
            return;
        Message message = new Message(buf.array());
        Record[] records = message.getSectionArray(1);
        for (Record record : records) {
            if (record instanceof ARecord) {
                ARecord aRecord = (ARecord) record;
                int id = message.getHeader().getID();
                SelectionKey key = dnsMap.get(id);
                if (key == null)
                    continue;
                Attachment attachment = (Attachment) key.attachment();
                attachment.setHost(aRecord.getAddress());
               startMessageMenager.connectHost(attachment, attachment.getHost(), attachment.getPort(),selector);
               startMessageMenager.OkAnswerClient(attachment,attachment.getHost(), (byte) attachment.getPort());
                return;
            }
        }
    }

    private void accept(SelectionKey key){
        SocketChannel clientChannel = null;
        try {
            clientChannel = ((ServerSocketChannel)key.channel()).accept();
            clientChannel.configureBlocking(false);
            Attachment client = new Attachment(clientChannel, selector);
            clientChannel.register(selector,SelectionKey.OP_READ, client);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                assert clientChannel != null;
                clientChannel.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private void connect(SelectionKey key){
        SocketChannel channel = ((SocketChannel) key.channel());
        Attachment attachment = ((Attachment) key.attachment());
        attachment.deleteOpcion(SelectionKey.OP_CONNECT);
        try {
            channel.finishConnect();
            attachment.addOpcion(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            attachment.getOtherAttachment().addOpcion(SelectionKey.OP_READ  );
        } catch (IOException e) {
            e.printStackTrace();
            attachment.close();
        }
    }

    private void read(SelectionKey key){
        Attachment attachment = (Attachment) key.attachment();
        try {
            int byteRead = attachment.getSocketChannel().read(attachment.getBuf());
            System.out.println(new String(attachment.getBuf().array()));
            if (attachment.getOtherAttachment() == null) {
                if(attachment.isFirstMessage()){
                    startMessageMenager.greetingMessage(attachment);
                }else{
                    startMessageMenager.headersMessage(key,attachment, byteRead, selector);
                }
            } else {
                Attachment otherAttachment = attachment.getOtherAttachment();
                if (byteRead > 0 && otherAttachment.getSocketChannel().isConnected()) {
                    otherAttachment.addOpcion(SelectionKey.OP_WRITE);
                }
                if (byteRead == -1) {
                    attachment.deleteOpcion(SelectionKey.OP_READ);
                    attachment.setFinishRead(true);
                    if (attachment.getBuf().position() == 0) {
                        otherAttachment.getSocketChannel().shutdownOutput();
                        otherAttachment.setFinishWrite(true);
                        if (attachment.isFinishWrite() || otherAttachment.getBuf().position() == 0) {
                            attachment.close();
                            otherAttachment.close();
                        }
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
            attachment.close();
        }

    }

    private void write(SelectionKey key){
        Attachment attachment = (Attachment) key.attachment();
        Attachment otherAttachment = attachment.getOtherAttachment();
        otherAttachment.getBuf().flip();
        try {
            int byteWrite = attachment.getSocketChannel().write(otherAttachment.getBuf());
            if (byteWrite > 0 ){
                otherAttachment.getBuf().compact();
                otherAttachment.addOpcion(SelectionKey.OP_READ);
            }
            if(otherAttachment.getBuf().position() == 0){
                attachment.deleteOpcion(SelectionKey.OP_WRITE);
                if (otherAttachment.isFinishRead()) {
                    attachment.getSocketChannel().shutdownOutput();
                    attachment.setFinishWrite(true);
                    if (otherAttachment.isFinishWrite()) {
                        attachment.close();
                        otherAttachment.close();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
