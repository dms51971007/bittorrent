package com.dms.bittorent;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Created by r2 on 24.11.2016.
 */
public class HTTPMessages {

    public enum HTTPMessageType {
        CHOKE(0),
        UNCHOKE(1),
        INTERESTED(2),
        NOT_INTERESTED(3),
        HAVE(4),
        BITFIELD(5),
        REQUEST(6),
        PIECE(7),
        CANCEL(8);

        private byte id;

        HTTPMessageType(int id) {
            this.id = (byte) id;
        }

        public boolean equals(byte c) {
            return this.id == c;
        }


        public byte getTypeByte() {
            return this.id;
        }
    }

    public static final int MESSAGE_LEN = 4;
    public static final int REQUEST_LEN = 13;
    public static final int HAVE_LEN = 4;


    static final int HS_LENGTH = 49;
    public static final String PROTOCOL_ID = "BitTorrent protocol";

    private HTTPMessageType type;

    public HTTPMessages() {
        this.type = type;
    }


    public static ByteBuffer getHandShakeMessage(String PEER_ID, byte[] info_hash) {

        ByteBuffer buffer = ByteBuffer.allocate(HS_LENGTH + PROTOCOL_ID.length());
        buffer.put((byte) PROTOCOL_ID.length());
        buffer.put(PROTOCOL_ID.getBytes(StandardCharsets.ISO_8859_1));
        buffer.put(new byte[8]);
        buffer.put(info_hash);
        buffer.put(PEER_ID.getBytes(StandardCharsets.ISO_8859_1));
        buffer.rewind();

        return buffer;
    }

    public static HTTPMessageType readMessage(byte[] mess) {
        if (mess == null || mess.length == 0) return null;
        try {
        return HTTPMessageType.values()[mess[0]];} catch (ArrayIndexOutOfBoundsException e){
            return null;
        }
    }


    public static ByteBuffer getByteMessage(HTTPMessageType type) {
        if (type == HTTPMessageType.CHOKE) {
            return getByteChocke();
        } else if (type == HTTPMessageType.UNCHOKE) {
            return getByteChocke();
        } else if (type == HTTPMessageType.INTERESTED) {
            return getByteInterested();
        } else if (type == HTTPMessageType.REQUEST) {
            return getByteRequest();
        }


        return null;
    }

    private static ByteBuffer getByteChocke() {

        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + 1);
        buffer.putInt(1);
        buffer.put(HTTPMessageType.CHOKE.getTypeByte());
        buffer.rewind();
        return buffer;


    }

    private static ByteBuffer getByteRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + HAVE_LEN);
        buffer.putInt(REQUEST_LEN);
        buffer.put(HTTPMessageType.REQUEST.getTypeByte());
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(16384);

        buffer.rewind();
        return buffer;
    }

    public static ByteBuffer getByteHave(int numPiece) {
        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + 1 + HAVE_LEN);
        buffer.putInt(HAVE_LEN + 1);
        buffer.put(HTTPMessageType.HAVE.getTypeByte());
        buffer.putInt(numPiece);
        buffer.rewind();
        return buffer;
    }


    public static ByteBuffer getByteRequest1(int piece, int offset, int size) {
        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + REQUEST_LEN);
        buffer.putInt(REQUEST_LEN);
        buffer.put(HTTPMessageType.REQUEST.getTypeByte());
        buffer.putInt(piece);
        buffer.putInt(offset);
        buffer.putInt(size);

        buffer.rewind();
        return buffer;
    }

    private static ByteBuffer getByteUnChocke() {
        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + 1);
        buffer.putInt(1);
        buffer.put(HTTPMessageType.UNCHOKE.getTypeByte());
        buffer.rewind();
        return buffer;
    }

    private static ByteBuffer getByteInterested() {
        ByteBuffer buffer = ByteBuffer.allocate(MESSAGE_LEN + 1);
        buffer.putInt(1);
        buffer.put(HTTPMessageType.INTERESTED.getTypeByte());
        buffer.rewind();
        return buffer;
    }


}

