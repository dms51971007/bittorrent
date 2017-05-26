package com.dms.bittorent;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by r2 on 01.12.2016.
 */
public class UDPMessages {
    public enum UDPMessageType {
        UNKNOWN(-1),
        CONNECT_REQUEST(0),
        CONNECT_RESPONSE(0),
        ANNOUNCE_REQUEST(1),
        ANNOUNCE_RESPONSE(1),
        SCRAPE_REQUEST(2),
        SCRAPE_RESPONSE(2),
        ERROR(3);

        private final int id;

        UDPMessageType(int id) {
            this.id = id;
        }

        public int getId() {
            return this.id;
        }
    }

    ;

    private static final int REQUEST_MESSAGE_SIZE = 16;
    private static final long REQUEST_MAGIC = 0x41727101980L;
    private static final int RESPONSE_MESSAGE_SIZE = 16;
    private static final int ANNOUNCE_MESSAGE_SIZE = 98;

    private static final int ANNOUNCE_RESPONSE_MIN_MESSAGE_SIZE = 20;

    public static ByteBuffer getByteRequest(int Id) {
        ByteBuffer data = ByteBuffer
                .allocate(REQUEST_MESSAGE_SIZE);
        data.putLong(REQUEST_MAGIC);
        data.putInt(UDPMessageType.CONNECT_REQUEST.getId());
        data.putInt(Id);
        data.rewind();
        return data;
    }

    public static ByteBuffer getByteAnounce(int transactionId, Long connectionId, String peerId, byte[] infoHash) {

        ByteBuffer data = ByteBuffer.allocate(ANNOUNCE_MESSAGE_SIZE);
        data.putLong(connectionId);
        data.putInt(UDPMessageType.ANNOUNCE_REQUEST.getId());
        data.putInt(transactionId);
        data.put(infoHash);
        data.put(peerId.getBytes());
        data.putLong(90); //downloaded
        data.putLong(90);     //left
        data.putLong(90); //uploaded
        data.putInt(0); //event
        data.putInt(0); //ip
        data.putInt(0);
        data.putInt(10000);
        data.putShort((short) 6882);
        return data;
    }

    public static UDPMessageType readMessage(ByteBuffer data) throws IOException {
        final int UDP_MIN_RESPONSE_PACKET_SIZE = 8;

        if (data.remaining() < UDP_MIN_RESPONSE_PACKET_SIZE) {
            throw new IOException("Invalid packet size!");
        }

        data.mark();
        int action = data.getInt();
        data.reset();

        if (action == UDPMessageType.CONNECT_RESPONSE.getId()) {
            return readConnectResponse(data);
        } else if (action == UDPMessageType.ANNOUNCE_RESPONSE.getId()) {
            return readAnnonceResponse(data);
        } else if (action == UDPMessageType.ERROR.getId()) {
        }

        throw new IOException("Unknown UDP tracker response message!");
    }

    private static UDPMessageType readAnnonceResponse(ByteBuffer data) throws IOException {
        if (data.remaining() < ANNOUNCE_RESPONSE_MIN_MESSAGE_SIZE ||
                (data.remaining() - ANNOUNCE_RESPONSE_MIN_MESSAGE_SIZE) % 6 != 0) {
            throw new IOException("Invalid announce response message size!");
        }

        if (data.getInt() != UDPMessageType.ANNOUNCE_RESPONSE.getId()) {
            throw new IOException("Invalid announce response message size!");
        }
        return UDPMessageType.ANNOUNCE_RESPONSE;
    }

    private static UDPMessageType readConnectResponse(ByteBuffer data) throws IOException {

        if (data.remaining() != RESPONSE_MESSAGE_SIZE) {
            throw new IOException("Invalid connect response message size!");
        }

        if (data.getInt() != UDPMessageType.CONNECT_RESPONSE.getId()) {
            throw new IOException("Invalid connect response message size!");
        }
        return UDPMessageType.CONNECT_RESPONSE;
    }

    ;


}
