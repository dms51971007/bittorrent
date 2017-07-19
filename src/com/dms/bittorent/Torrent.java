package com.dms.bittorent;

import com.dms.bittorent.bencoding.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by r2 on 22.11.2016.
 */


public class Torrent {
    // TODO исправить
    final String PEER_ID = "-BT2042-7da4d9d07789";
    Storage storage;
    Selector selector = Selector.open();
    private byte[] infoHash = new byte[20];
    private ArrayList<Peer> peers = new ArrayList<>();
    Lock peerLock = new ReentrantLock();

    public byte[] getInfoHash() {
        return infoHash;
    }


//    private ArrayList<InetSocketAddress> peers = new ArrayList<>();

    public ArrayList<Peer> getPeers() {
        return peers;
    }

    long totalTime = 0;

    private void downloadLoop(Updater updater) throws Exception {


        while (true) {
            updater.update();
            try {
                selector.select(5);
            } catch (IOException e) {
                break;
            }
            Set keys = selector.selectedKeys();

            Iterator it = keys.iterator();


            while (it.hasNext()) {
                SelectionKey sk = (SelectionKey) it.next();
                it.remove();

                if (!sk.isValid()) {
                    break;
                }
                Peer peer = (Peer) sk.attachment();


                SocketChannel channel = (SocketChannel) sk.channel();
                if (sk.isValid() && sk.isConnectable()) {
                    try {
                        channel.finishConnect();
                        sk.interestOps(SelectionKey.OP_WRITE);
                    } catch (Exception e) {
                    }
                }
                if (sk.isValid() && sk.isWritable()) {

                    if (!peer.isHandShake()) {
                        peer.setTimeRequest();
                        peer.write(ProtocolMessages.getHandShakeMessage(PEER_ID, infoHash));
                        sk.interestOps(SelectionKey.OP_READ);
                        continue;
                    } else {
                        for (int i = 0; i < storage.getNumPieces(); i++) {

                            if (storage.getBitField().get(i) != peer.getSentBitField().get(i)) {
                                peer.write(ProtocolMessages.getByteHave(i));
                                peer.getSentBitField().set(i);
                            }
                        }

                        if (!peer.getWriteBuffers().isEmpty()) {
                            ByteBuffer bb = peer.getWriteBuffers().get(0);

                            int count = peer.write(bb);

                            if (!bb.hasRemaining() || count == 0) {
                                peer.getWriteBuffers().remove(bb);
                            }
                        }
                        if (!peer.getWriteBuffers().isEmpty()) {
                            sk.interestOps(SelectionKey.OP_READ + SelectionKey.OP_WRITE);

                        } else {
                            sk.interestOps(SelectionKey.OP_READ);

                        }
                    }


                }
                if (sk.isValid() && sk.isReadable()) {
                    peer.readMessage2();
                    byte[] message;
                    if (!peer.isHandShake()) {
                        if (checkHandShake2(infoHash, peer)) {
                            peer.setHandShake(true);
                            peer.write(ProtocolMessages.getByteMessage(ProtocolMessages.HTTPMessageType.INTERESTED));
                            peer.setLastLog();
                            peer.setTimeRequest();
                            sk.interestOps(SelectionKey.OP_WRITE);
                        }
                    } else {
                        while ((message = peer.readMessage()) != null) {


                            ProtocolMessages.HTTPMessageType messageType = ProtocolMessages.readMessage(message);
                            if (messageType != null) peer.setLastLog();

                            if (messageType == ProtocolMessages.HTTPMessageType.REQUEST) {
                                System.out.println(messageType);

                                peer.getWriteBuffers().add(getPieceByRequest(message));
                                sk.interestOps(SelectionKey.OP_WRITE);


                            } else if (messageType == ProtocolMessages.HTTPMessageType.INTERESTED) {
                                peer.write(ProtocolMessages.getByteMessage(ProtocolMessages.HTTPMessageType.UNCHOKE));

                            } else if (messageType == ProtocolMessages.HTTPMessageType.BITFIELD) {
                                updateBitField(peer, message);
                                peer.write(ProtocolMessages.getByteMessage(ProtocolMessages.HTTPMessageType.INTERESTED));

                            } else if (messageType == ProtocolMessages.HTTPMessageType.HAVE) {
                                peer.write(ProtocolMessages.getByteMessage(ProtocolMessages.HTTPMessageType.INTERESTED));
                                updateHAVE(peer, message);
                            } else if (messageType == ProtocolMessages.HTTPMessageType.UNCHOKE) {
                                peer.setChocked(true);
                                peer.write(ProtocolMessages.getByteMessage(ProtocolMessages.HTTPMessageType.INTERESTED));
                                requestNextPiece(peer);

                            } else if (messageType == ProtocolMessages.HTTPMessageType.PIECE) {
                                //TODO проверка номера и размера
                                long startTime = new Date().getTime();

                                byte[] requestData = Arrays.copyOfRange(message, 9, message.length);
                                try {
                                    System.arraycopy(requestData, 0, peer.getPiece().getPieceData(), peer.getPieceIndex(), requestData.length);
                                } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                                    e.printStackTrace();
                                }
                                //TODO УЖАС!!!!
                                requestNextPiece(peer);
                                this.totalTime += new Date().getTime() - startTime;
                                if (peer.getPiece() == null) {
                                    tryToKillSlowPeer(peer);
                                }
                            }
                        }
                    }

                }

            }

            keys.clear();
        }

    }

    private ByteBuffer getPieceByRequest(byte[] message) {

        ByteBuffer bb = java.nio.ByteBuffer.wrap(Arrays.copyOfRange(message, 1, 13));

        int pieceIndex = bb.getInt();
        int offset = bb.getInt();
        int length = bb.getInt();

        byte[] b = null;
        byte[] a = null;

        try {
            a = storage.readPieceCache(storage.getPieceByIndex(pieceIndex));
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (a != null)
            return ProtocolMessages.getBytePiece(pieceIndex, offset, Arrays.copyOfRange(a, offset, offset + length));
        else return null;
    }

    private void tryToKillSlowPeer(Peer p) {

        for (SelectionKey sk : selector.keys()) {
            Peer peerToKill = (Peer) sk.attachment();

            // TODO getPieceIndex перенести в Piece
            if (peerToKill.getPiece() != null && peerToKill.getSpeedRequest() > p.getSpeedRequest() && p.getRecieveBitField().get(storage.getPieceIndex(peerToKill.getPiece()))) {
                try {
                    System.out.println("Close peer" + peerToKill);
                    sk.channel().close();
                    sk.cancel();
                    peerToKill.setBadPeer();
                    peerToKill.free();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    requestNextPiece(p);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println(p.getPiece());
                return;
            }
        }


    }


    private boolean requestNextPiece(Peer peer) throws Exception {
        final int PACKET_SIZE = 16384;


        if (peer.getPiece() == null) {
            Piece newPiece = storage.getFreePiece(peer.getRecieveBitField());
            if (newPiece == null) return false;
            peer.setTimeRequest();
            peer.setPiece(newPiece);
            peer.setPieceIndex(0);
            peer.getPiece().setPieceData(new byte[storage.getPieceSize(peer.getPiece())]);
            // System.out.println(" " + peer.getIsa() + "|" + newPiece);
        } else {
            peer.setPieceIndex(peer.getPieceIndex() + PACKET_SIZE);
        }

        int pl = storage.getPieceSize(peer.getPiece());
        int numPiece = storage.getPieceIndex(peer.getPiece());

        int questSize = (pl - peer.getPieceIndex() > PACKET_SIZE) ? PACKET_SIZE : pl - peer.getPieceIndex();

        if (peer.getPieceIndex() >= pl) {
//            System.out.println(storage.checkPiece(peer.getPiece(), peer.getPieceData()));
            peer.getPiece().getLock().unlock();
            storage.writePiece(peer.getPiece(), peer);

           // peer.write(ProtocolMessages.getByteHave(storage.getPieceIndex(peer.getPiece())));
            peer.setPiece(null);
            peer.attempts++;
            //TODO полечить
            requestNextPiece(peer);
        } else
            peer.write(ProtocolMessages.getByteRequest1(numPiece, peer.getPieceIndex(), questSize));
        return true;
    }

    private boolean checkHandShake2(byte[] info_hash, Peer peer) {
        byte[] bytesMessages = peer.getBytesMessages();
        if (bytesMessages == null) return false;
        if (bytesMessages.length == 0) return false;

        if (bytesMessages.length < bytesMessages[0] + ProtocolMessages.HS_LENGTH - 1) return false;
        if (bytesMessages[0] < 1) return false;
        String header = new String(Arrays.copyOfRange(bytesMessages, 1, bytesMessages[0] + 1));

        if (!header.equals(ProtocolMessages.PROTOCOL_ID)) return false;

        byte[] infoHashR = Arrays.copyOfRange(bytesMessages, bytesMessages[0] + 9, bytesMessages[0] + 9 + 20);

        if (!Arrays.equals(infoHashR, info_hash))
            return false;
        peer.setPeerID(Arrays.copyOfRange(bytesMessages, bytesMessages[0] + 9 + 20, bytesMessages[0] + 9 + 20 + 20));

        peer.setBytesMessages(Arrays.copyOfRange(bytesMessages, bytesMessages[0] + 9 + 20 + 20, bytesMessages.length));

        return true;
    }


    private void updateBitField(Peer peer, byte[] bytes) {

        //BitSet bitfield = new BitSet(bytes.length * 8);
        for (int i = 8; i < bytes.length * 8; i++) {
            if ((bytes[i / 8] & (1 << (7 - (i % 8)))) > 0) {
                peer.getRecieveBitField().set(i - 8);
            }
        }

    }


    private void updateHAVE(Peer peer, byte[] bytes) {

        int i = java.nio.ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1, 5)).getInt();
        if (i > 0)
            peer.setBitField(i);
    }


    public Torrent(String dir, String file) throws Exception {
        ArrayList<BeRec> elements = new bencoding(file).getElements();
        BeDictionary root = (BeDictionary) elements.get(0);
        BeDictionary info = (BeDictionary) root.get("info");

        if (info.get("length") != null) {
            storage = new Storage("", dir);

            storage.addFile(info.get("name").toString(), info.get("length").getLong());
            storage.setLength(info.get("length").getLong());
        } else {
            String dirName = ConsoleHelper.fromBeToUTF(info.get("name").toString());
            storage = new Storage(dirName, dir);

            BeList files = (BeList) info.get("files");
            Long totalLength = 0L;

            for (BeRec file1 : files) {
                BeDictionary befile = (BeDictionary) file1;
                Long fileLength = befile.get("length").getLong();
                totalLength += fileLength;
                String strFile = "";

                for (BeRec br : (BeList) (befile.get("path").getList())) {
                    strFile = strFile + "/" + br.getValue();
                }
                storage.addFile(ConsoleHelper.fromBeToUTF(strFile), fileLength);
            }

            storage.setLength(totalLength);
        }
        Long pieceLength = info.get("piece length").getLong();
        this.infoHash = ConsoleHelper.hash(info.beCode().getBytes(StandardCharsets.ISO_8859_1));

        String hash = (String) info.get("pieces").getValue();

        for (int i = 0; i < (hash.length() / 20); i++) {
            storage.addPiece(hash.substring(i * 20, (i + 1) * 20).getBytes(StandardCharsets.ISO_8859_1));
        }
        storage.setPieceLength(pieceLength);

        storage.setBitField();

        //peers.add(new Peer(new InetSocketAddress("192.168.1.219", 40051), storage.getNumOfPieces()));
//        peers.add(new Peer(new InetSocketAddress("192.168.1.10", 49158)));

        Thread storageWriter = new Thread(storage);
        Thread announcer = new Thread(new Announcer(this, (BeList) root.get("announce-list")));
        announcer.start();

        storageWriter.start();
        //storage.checkAll();

        Updater updater = new Updater(this, 5);


        downloadLoop(updater);


        ConsoleHelper.writeMessageLn("OK");

    }

}
