package com.company;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Message;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

class PortForwarder {
    private StartMessageMenager startMessageMenager = new StartMessageMenager();
    private Selector selector = null;
    static DatagramChannel dnsChannel;
    private SelectionKey dnsKey;
    static HashMap<Integer, Attachment> connectionsDns = new HashMap<>();

    void start(int port) throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverSocket;
        serverSocket = ServerSocketChannel.open();
        assert serverSocket != null;
        serverSocket.bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        String[] dnsServers = ResolverConfig.getCurrentConfig().servers();
        dnsChannel = DatagramChannel.open();
        dnsChannel.configureBlocking(false);
        //System.out.println(dnsServers.length);
        for (String server : dnsServers) {
            System.out.println(server);
        }
        if (dnsServers.length > 2) {
            dnsChannel.connect(new InetSocketAddress(dnsServers[2], 53));
        } else {
            dnsChannel.connect(new InetSocketAddress("8.8.8.8", 53));
        }
        dnsKey = dnsChannel.register(selector, SelectionKey.OP_READ);
        while (true) {
            selector.select();
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();
            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                if(dnsKey == key) {
                    if (key.isReadable()) {
                        System.out.println("dns");
                        resolvedDns();
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

    private void resolvedDns() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int len = dnsChannel.read(buffer);
        if (len <= 0) return;
        Message msg = new Message(buffer.array());
        Record[] recs = msg.getSectionArray(1);
        for (Record rec : recs) {
            if (rec instanceof ARecord) {
                ARecord aRecord= (ARecord) rec;
                int id = msg.getHeader().getID();
                Attachment attachment = connectionsDns.get(id);
                if (attachment == null) continue;
                InetAddress adr = aRecord.getAddress();
                startMessageMenager.connectHost(attachment,adr, attachment.getPort(),selector);
            }
        }
    }

    private void accept(SelectionKey key){
        SocketChannel clientChannel = null;
        try {
            clientChannel = ((ServerSocketChannel)key.channel()).accept();
            clientChannel.configureBlocking(false);
            Attachment client = new Attachment(clientChannel, selector, "Client");
            clientChannel.register(selector,SelectionKey.OP_READ, client);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                assert clientChannel != null;
                clientChannel.close();
                dnsChannel.close();
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
            //System.out.println(Arrays.toString(attachment.getBuf().array()));
            System.out.println(new String(attachment.getBuf().array()));
            if (attachment.getOtherAttachment() == null) {
                if(attachment.isFirstMessage()){
                    startMessageMenager.greetingMessage(attachment);
                }else{
                    startMessageMenager.headersMessage(attachment, byteRead, selector);
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
