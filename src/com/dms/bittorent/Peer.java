package com.dms.bittorent;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by r2 on 02.12.2016.
 */
public class Peer {

    private InetSocketAddress isa;
    private final Lock lock = new ReentrantLock();
    private long lastLog;

    private boolean isChocked;
    private boolean isInterest;
    private boolean isHandShake;
    private boolean isUsed;

    private byte[] bytesMessages;

    void putBytesMessages(byte[] inBytes) {
        if (bytesMessages == null) {
            bytesMessages = inBytes;
        } else {
            byte[] newBytes = new byte[bytesMessages.length + inBytes.length];
        }
    }

  /* byte[] getBytesMessage() {

    }*/

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
        setBadPeer();
        setUsed(false);
        setInterest(false);
        setHandShake(false);
        setChocked(false);
        if (getPiece() != null)
            getPiece().setUsed(false);
        badPacket++;

    }

    private Piece piece;
    private int pieceIndex;
    private byte[] pieceData;


    public boolean isHandShake() {
        return isHandShake;
    }

    public void setHandShake(boolean handShake) {
        isHandShake = handShake;
    }

    public byte[] getPieceData() {
        return pieceData;
    }

    public void setPieceData(byte[] pieceData) {
        this.pieceData = pieceData;
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

    private boolean isChocked;
    private boolean isInterest;

    private Piece piece;
    private int pieceIndex;

    private byte[] pieceData;

    public byte[] getPieceData() {
        return pieceData;
    }

    public void setPieceData(byte[] pieceData) {
        this.pieceData = pieceData;
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

    Long goodPacket = Long.valueOf(0);
    Long badPacket = Long.valueOf(0);
    Long goodHandShake = Long.valueOf(0);

    public Integer getProcesDownload() {
        return procesDownload;
    }

    public void setProcesDownload(Integer procesDownload) {
        this.procesDownload = procesDownload;
    }

    Integer procesDownload;
    Long attempts = Long.valueOf(0);
    boolean badPeer = false;
    BitSet BitField = new BitSet();
    StringBuilder s = new StringBuilder();

    byte[] peerID = new byte[20];

    public void setBitField(BitSet bitField) {
        BitField = bitField;
    }

    public void setBitField(int i) {
        BitField.set(i);
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
        this.goodHandShake++;
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

    public Peer(InetSocketAddress isa) {

        this.isa = isa;
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
                ", bad=" + badPacket +
                ", hs=" + isHandShake() +
                ", lastlog=" + fromLastLog() +
                '}';
    }
}
