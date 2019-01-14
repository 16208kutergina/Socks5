package com.company;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class StartMessageMenager {
    private static byte version = 0x05;

    public void headersMessage(Attachment attachment, int byteRead, Selector selector) throws IOException {
        if(checkRequest(attachment.getBuf().array())){
            InetAddress host = getAddress(attachment.getBuf().array());
            int port = getPort(attachment.getBuf().array(), byteRead);
            connectHost(attachment, host, port,selector);
            assert host != null;
            OkAnswerClient(attachment, host, (byte) port);
        }else {
            attachment.close();
        }
    }

    private void OkAnswerClient(Attachment attachment, InetAddress host, byte port) throws IOException {
        byte[] answer = {version,
                ExceptionType.success,
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
      //  attachment.getOtherAttachment().addOpcion(SelectionKey.OP_WRITE);
    }

    private void connectHost(Attachment attachment, InetAddress host, int port, Selector selector) throws IOException {
        SocketChannel newHostChannel;
        newHostChannel = SocketChannel.open();
        newHostChannel.configureBlocking(false);
        newHostChannel.connect(new InetSocketAddress(host,port));
        Attachment newHost = new Attachment(newHostChannel,selector);
        attachment.setOtherAttachment(newHost);
        newHost.setOtherAttachment(attachment);
        attachment.getBuf().clear();
            attachment.getOtherAttachment().getBuf().clear();
        newHostChannel.register(selector, SelectionKey.OP_CONNECT, newHost);
    }

    public void greetingMessage(Attachment attachment) throws IOException {
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

    private boolean checkRequest(byte[] buf){
        return buf[0]==version
                && buf[1] == MethodType.connect
                && buf[3] == AddressType.IPv4;
    }

    private InetAddress getAddress(byte[] buf) throws UnknownHostException {
        if (buf[3] == AddressType.IPv4) {
            byte[] addr = new byte[]{buf[4], buf[5], buf[6], buf[7]};
            System.out.println(InetAddress.getByAddress(addr));
            return InetAddress.getByAddress(addr);
        }
        return null;
    }

    public int getPort(byte[] buf, int lenBuf){
        // System.out.println(dest_port);
        return (((0xFF & buf[lenBuf-2]) << 8) + (0xFF & buf[lenBuf-1]));
    }

    private boolean checkGreeting(byte[] buf){
        return buf[0] == version
                && buf[1] != 0
                && buf[2] == AuthenticationType.noAuthenticationRequired;
    }
}
