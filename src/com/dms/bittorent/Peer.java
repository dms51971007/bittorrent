package com.dms.bittorent;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by r2 on 02.12.2016.
 */
public class Peer {

    private SocketChannel socketChannel;
    private InetSocketAddress isa;

    private final Lock lock = new ReentrantLock();
    private long lastLog;

    private boolean isChocked;
    private boolean isInterest;
    private boolean isHandShake;
    private boolean isUsed;
    private boolean badPeer = false;


    Long goodPacket = Long.valueOf(0);
    private Long badPacket = Long.valueOf(0);
    private Long badHandShake = Long.valueOf(0);

    public Long getBadHandShake() {
        return badHandShake;
    }

    public void addBadHandShake(int badHandShake) {
        this.badHandShake += badHandShake;
    }


    Long attempts = Long.valueOf(0);


    private BitSet recieveBitField;

    private BitSet sentBitField;

    private byte[] peerID = new byte[20];

    public BitSet getRecieveBitField() {
        return recieveBitField;
    }

    public void setBytesMessages(byte[] bytesMessages) {
        this.bytesMessages = bytesMessages;
    }

    private byte[] bytesMessages;

    private List<ByteBuffer> writeBuffers = new ArrayList<>();

    private Piece piece;
    private int pieceIndex;

    private long timeRequest;

    public long getSpeedRequest() {
        return new Date().getTime() - this.timeRequest;
    }

    public List<ByteBuffer> getWriteBuffers() {
        return writeBuffers;
    }

    public void setTimeRequest() {
        this.timeRequest = new Date().getTime();
    }

    public void setPeerID(byte[] peerID) {
        this.peerID = peerID;
    }

    public byte[] getBytesMessages() {
        return bytesMessages;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public void setSocketChannel(Selector selector) {
        try {
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(this.getIsa());
            this.free();
            int ops = socketChannel.validOps();
            this.setHandShake(false);
            socketChannel.register(selector, ops, this);
            this.setLastLog();
            this.setUsed(true);
            this.addBadHandShake(1);
            //TODO не хорошо
            this.sentBitField = new BitSet(recieveBitField.size() - 1);

        } catch (IOException e) {
        }
    }

    public void readMessage2() {


        try {
            ByteBuffer buffer = ByteBuffer.allocate(20000);
            int numBytes = socketChannel.read(buffer);
            if (numBytes > 0)
                this.putBytesMessages(Arrays.copyOfRange(buffer.array(), 0, numBytes));

        } catch (IOException e) {

        } catch (NotYetConnectedException e) {

        }
    }


    public int write(ByteBuffer byteBuffer) {

        try {
            return this.socketChannel.write(byteBuffer);
        } catch (IOException e) {
            return 0;
        }

    }


    public void putBytesMessages(byte[] inBytes) {
        if (inBytes.length == 0) return;
        if (bytesMessages == null) {
            bytesMessages = inBytes;
        } else {
            bytesMessages = joinArrays(bytesMessages, inBytes);
        }
    }

    public static byte[] joinArrays(byte[] first, byte[] second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        byte[] res = new byte[first.length + second.length];

        System.arraycopy(first, 0, res, 0, first.length);
        System.arraycopy(second, 0, res, first.length, second.length);

        return res;
    }

    public byte[] readMessage() {

        if (bytesMessages == null) return null;
        if (bytesMessages.length < ProtocolMessages.MESSAGE_LEN) return null;
        int size = ByteBuffer.wrap(Arrays.copyOfRange(bytesMessages, 0, ProtocolMessages.MESSAGE_LEN)).getInt();
        if (bytesMessages.length < (ProtocolMessages.MESSAGE_LEN + size))
            return null;

        byte[] res = Arrays.copyOfRange(bytesMessages, ProtocolMessages.MESSAGE_LEN, ProtocolMessages.MESSAGE_LEN + size);
        bytesMessages = Arrays.copyOfRange(bytesMessages, size + ProtocolMessages.MESSAGE_LEN, bytesMessages.length);
        return res;
    }


    public int fromLastLog() {
        return (int) (new Date().getTime() - lastLog) / 1000;
    }

    public void setLastLog() {
        this.lastLog = new Date().getTime();
    }

    public boolean isUsed() {
        return isUsed;
    }

    public void setUsed(boolean used) {
        isUsed = used;
    }

    public void free() {
        //setBadPeer();
        this.setUsed(false);
        this.setInterest(false);
        this.setHandShake(false);
        this.setChocked(false);
        this.bytesMessages = null;
        if (this.getPiece() != null)
            this.getPiece().setUsed(false);
        this.setPiece(null);
        this.badPacket++;

    }


    public boolean isHandShake() {
        return isHandShake;
    }

    public void setHandShake(boolean handShake) {
        isHandShake = handShake;
        if (handShake)
            this.badHandShake = 0L;
    }


    public int getPieceIndex() {
        return pieceIndex;
    }

    public void setPieceIndex(int pieceIndex) {
        this.pieceIndex = pieceIndex;
    }

    public Piece getPiece() {
        return piece;
    }

    public void setPiece(Piece piece) {
        this.piece = piece;
    }

    public boolean isChocked() {
        return isChocked;
    }

    public void setChocked(boolean chocked) {
        isChocked = chocked;
    }

    public boolean isInterest() {
        return isInterest;
    }

    public void setInterest(boolean interest) {
        isInterest = interest;
    }

    public BitSet getSentBitField() {
        return sentBitField;
    }

    public void setRecieveBitField(BitSet recieveBitField) {
        this.recieveBitField = recieveBitField;
    }

    public void setBitField(int i) {
        recieveBitField.set(i);
    }

    public String getPeerID() {
        if (peerID[0] != 0)
            return new String(peerID, StandardCharsets.ISO_8859_1);
        else return "      ";
    }

    public Long getGoodPacket() {
        return goodPacket;
    }

    public void resetGoodPacket() {
        this.goodPacket = Long.valueOf(0);
    }


    public Long getBadPacket() {
        return badPacket;
    }

    public Boolean isBadPeer() {
        return badPeer;
    }

    public void setBadPeer() {
        this.badPeer = true;
    }

    public void incGoodPacket() {
        this.goodPacket++;
    }

    public void incBadPacket() {
        this.badPacket++;
    }

    public void incGoodHandShake() {
        this.badHandShake++;
    }

    public void incAttepmpts() {
        this.attempts++;
    }


    public InetSocketAddress getIsa() {
        return isa;
    }

    public Lock getLock() {

        return lock;
    }

    public Peer(InetSocketAddress isa, int sizeBitField) {

        this.isa = isa;
        sentBitField = new BitSet(sizeBitField - 1);
        recieveBitField = new BitSet(sizeBitField - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Peer peer = (Peer) o;

        return isa != null ? isa.getAddress().equals(peer.isa.getAddress()) && isa.getPort() == peer.isa.getPort() : peer.isa == null;

    }

    @Override
    public int hashCode() {
        return isa != null ? isa.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Peer{" +
                "a=" + attempts +
                ", isa=" + isa +
                ", piece=" + piece +
                ", used=" + isUsed() +
                ", bhs=" + getBadHandShake() +
                ", hs=" + isHandShake() +
                ", speed=" + getSpeedRequest() +
                ", lastlog=" + fromLastLog() +
                '}';
    }
}
