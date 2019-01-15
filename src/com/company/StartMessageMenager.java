package com.company;

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static com.company.PortForwarder.*;

class StartMessageMenager {
    private static byte version = 0x05;

    void headersMessage(SelectionKey key, Attachment attachment, int byteRead, Selector selector) throws IOException {
        byte[] array = attachment.getBuf().array();
        if(checkRequestIPV4(array)){
            InetAddress host = getAddress(array);
            int port = getPort(array, byteRead);
            connectHost(attachment, host, port,selector);
            assert host != null;
            OkAnswerClient(attachment, host, (byte) port);
        }
        if(checkRequestDOMAIN(array)){
            int length = array[4];
            StringBuilder domain = new StringBuilder();
            int i;
            for(i = 5; i < 5+length; i++){
                domain.append((char) array[i]);
            }
            int port = (((0xFF & array[i]) << 8) + (0xFF & array[i+1]));
            attachment.setPort(port);
            Name name = org.xbill.DNS.Name.fromString(domain.toString(), Name.root);
            Record rec = Record.newRecord(name, Type.A, DClass.IN);
            Message msg = Message.newQuery(rec);
            dnsChannel.write(ByteBuffer.wrap(msg.toWire()));
            dnsMap.put(msg.getHeader().getID(), key);
        }
    }

    void OkAnswerClient(Attachment attachment, InetAddress host, byte port) throws IOException {
        byte[] answer = {version,
                StatusType.success,
                0x00,
                AddressType.IPv4,
                host.getAddress()[0],
                host.getAddress()[1],
                host.getAddress()[2],
                host.getAddress()[3],
                0x00,
                port};
        attachment.getSocketChannel().write(ByteBuffer.wrap(answer));
        attachment.getBuf().clear();
    }

    void connectHost(Attachment attachment, InetAddress host, int port, Selector selector) throws IOException {
        SocketChannel newHostChannel;
        newHostChannel = SocketChannel.open();
        newHostChannel.configureBlocking(false);
        newHostChannel.connect(new InetSocketAddress(host,port));
        Attachment newHost = new Attachment(newHostChannel,selector);
        attachment.setOtherAttachment(newHost);
        newHost.setOtherAttachment(attachment);
        attachment.getBuf().clear();
            attachment.getOtherAttachment().getBuf().clear();
        newHostChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ, newHost);
    }

    void greetingMessage(Attachment attachment) throws IOException {
        if(checkGreeting(attachment.getBuf().array())){
            byte[] answer = {version, AuthenticationType.noAuthenticationRequired};
            attachment.getSocketChannel().write(ByteBuffer.wrap(answer));
            attachment.getBuf().clear();
            attachment.setFirstMessage(false);
        }else {
            byte[] answer = {version, AuthenticationType.noMethods};
            attachment.getSocketChannel().write(ByteBuffer.wrap(answer));
            attachment.close();
        }
    }

    private boolean checkRequestIPV4(byte[] buf){
        return buf[0]==version
                && buf[1] == MethodType.connect
                && buf[3] == AddressType.IPv4;
    }

    private boolean checkRequestDOMAIN(byte[] buf){
        return buf[0]==version
                && buf[1] == MethodType.connect
                && buf[3] == AddressType.DOMAIN;
    }

    private InetAddress getAddress(byte[] buf) throws UnknownHostException {
        if (buf[3] == AddressType.IPv4) {
            byte[] addr = new byte[]{buf[4], buf[5], buf[6], buf[7]};
            System.out.println(InetAddress.getByAddress(addr));
            return InetAddress.getByAddress(addr);
        }
        return null;
    }

    private int getPort(byte[] buf, int lenBuf){
        return (((0xFF & buf[lenBuf-2]) << 8) + (0xFF & buf[lenBuf-1]));
    }

    private boolean checkGreeting(byte[] buf){
        return buf[0] == version
                && buf[1] != 0
                && buf[2] == AuthenticationType.noAuthenticationRequired;
    }


}
