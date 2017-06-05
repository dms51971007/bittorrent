package com.dms.bittorent;

import com.dms.bittorent.Exceptions.BadHandshake;
import com.dms.bittorent.Exceptions.BadPeer;
import com.dms.bittorent.Exceptions.BadPiece;
import com.dms.bittorent.bencoding.*;

import java.io.*;
import java.net.*;
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
    final String PEER_ID = "-BT1042-7da4d9d07789";
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


    public SocketChannel downloadLoop(Updater updater) throws Exception {

        final int HS_LENGTH = 49;
        final String PROTOCOL_ID = "BitTorrent protocol";

        while (true) {
            try {
                selector.select(10);
            } catch (IOException e) {
                break;
            }
            updater.update();
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
                if (sk.isConnectable()) {
                    channel.finishConnect();
                    sk.interestOps(SelectionKey.OP_WRITE);
                }

                if (sk.isWritable()) {

                    SocketChannel ch = (SocketChannel) sk.channel();
                    if (!peer.isHandShake()) {
                        peer.setLastLog();
                        ch.write(HTTPMessages.getHandShakeMessage(PEER_ID, infoHash));
                    } else {
                        for (int i = 0; i < storage.getNumPieces(); i++) {
                            if (storage.isValidPiece(i)) {
                                channel.write(HTTPMessages.getByteHave(i));
                            }
                        }
                    }
                    sk.interestOps(SelectionKey.OP_READ);


                }
                if (sk.isReadable()) {
                    SocketChannel ch = (SocketChannel) sk.channel();

                    if (!peer.isHandShake()) {
                        if (checkHandShake(ch, infoHash, peer)) {
                            peer.setHandShake(true);
                            ch.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.INTERESTED));
                            peer.setLastLog();
                            sk.interestOps(SelectionKey.OP_WRITE);

                        }

                    }

                    if (peer.isHandShake()) {
                        byte[] message = readMessage(ch);
                        if (message == null) {
                            continue;
                        }
                        peer.setLastLog();

                        HTTPMessages.HTTPMessageType messageType = HTTPMessages.readMessage(message);
                        System.out.println(messageType + " " + peer.getIsa());
                        if (messageType == HTTPMessages.HTTPMessageType.BITFIELD) {
                            updateBitField(peer, message);
                            ch.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.INTERESTED));
                        }
                        if (messageType == HTTPMessages.HTTPMessageType.HAVE) {
                            ch.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.INTERESTED));
                            updateHAVE(peer, message);
                        }
                        if (messageType == HTTPMessages.HTTPMessageType.UNCHOKE) {
                            peer.setChocked(true);
                            ch.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.INTERESTED));
                            requestNextPiece(ch, peer);
                        }
                        if (messageType == HTTPMessages.HTTPMessageType.PIECE) {
                            //TODO проверка номера и размера
                            byte[] requestData = Arrays.copyOfRange(message, 9, message.length);
                            System.arraycopy(requestData, 0, peer.getPieceData(), peer.getPieceIndex(), requestData.length);
                            //TODO УЖАС!!!!
                            requestNextPiece(ch, peer);
                            if (peer.getPiece() == null) {
                                System.out.println("Done");
                                sk.cancel();
                            }
                        }
                    }

                }

            }

            keys.clear();
        }


        return null;
    }


    private boolean requestNextPiece(SocketChannel channel, Peer peer) throws Exception {
        final int PACKET_SIZE = 16384;


        if (peer.getPiece() == null) {
            Piece newPiece = storage.getFreePiece(peer.BitField);
            if (newPiece == null) return false;
            peer.setPiece(newPiece);
            peer.setPieceIndex(0);
            peer.setPieceData(new byte[storage.getPieceSize(peer.getPiece())]);
            // System.out.println(" " + peer.getIsa() + "|" + newPiece);
        } else {
            peer.setPieceIndex(peer.getPieceIndex() + PACKET_SIZE);
        }

        int pl = storage.getPieceSize(peer.getPiece());
        int numPiece = storage.getPieceIndex(peer.getPiece());

        int questSize = (pl - peer.getPieceIndex() > PACKET_SIZE) ? PACKET_SIZE : pl - peer.getPieceIndex();

        if (peer.getPieceIndex() >= pl) {
            storage.checkPiece(peer.getPiece(), peer.getPieceData());
            storage.writePiece(peer.getPiece(), peer.getPieceData());
            channel.write(HTTPMessages.getByteHave(storage.getPieceIndex(peer.getPiece())));
            peer.setPiece(null);
            peer.attempts++;
            //TODO полечить
            requestNextPiece(channel, peer);
        } else
            channel.write(HTTPMessages.getByteRequest1(numPiece, peer.getPieceIndex(), questSize));
        return true;
    }


    boolean checkHandShake(SocketChannel ch, byte[] info_hash, Peer peer) {

        ByteBuffer buffer = ByteBuffer.allocate(1);
        try {
            ch.read(buffer);
            int headerStr = buffer.get(0);

            buffer = ByteBuffer.allocate(HTTPMessages.HS_LENGTH + headerStr - 1);
            ch.read(buffer);
            buffer.rewind();
            byte[] pstr = new byte[headerStr];
            buffer.get(pstr);

            if (!(new String(pstr, StandardCharsets.ISO_8859_1)).contains(HTTPMessages.PROTOCOL_ID))
                return false;

            buffer.get(new byte[8]);
            byte[] infoHashR = new byte[20];
            buffer.get(infoHashR);
            buffer.get(peer.peerID);

            if (!Arrays.equals(infoHashR, info_hash))
                return false;
            return true;
        } catch (IOException e) {
        } catch (IllegalArgumentException e) {
        }

        return false;
    }


    private void updateBitField(Peer peer, byte[] bytes) {

        BitSet bitfield = new BitSet(bytes.length * 8);
        for (int i = 8; i < bytes.length * 8; i++) {
            if ((bytes[i / 8] & (1 << (7 - (i % 8)))) > 0) {
                bitfield.set(i - 8);
            }
        }

        peer.setBitField(bitfield);
    }


    private void updateHAVE(Peer peer, byte[] bytes) {

        int i = java.nio.ByteBuffer.wrap(Arrays.copyOfRange(bytes, 1, 5)).getInt();
        if (i > 0)
            peer.setBitField(i);
    }


    private byte[] readMessage(SocketChannel socketChannel) throws InterruptedException {

        try {
            //TODO разобраться
            ByteBuffer buffer = ByteBuffer.allocate(4);
            int numBytes = 0;
            final int MAXATTEMPS = 40;
            int attempts = 0;
            while ((numBytes) != 4) {
                attempts++;
                if (attempts > 3)
                    Thread.sleep(attempts);
                if (attempts > MAXATTEMPS)
                    return null;

                try {
                    numBytes += socketChannel.read(buffer);
                } catch (IOException e) {
                    return null;
                }
            }


            buffer.flip();
            int ii = buffer.getInt();

            if (ii < 20000)
                buffer = ByteBuffer.allocate(ii);
            else
                return null;
            numBytes = 0;
            attempts = 0;
            while ((numBytes) != ii) {
                attempts++;
                if (attempts > 3)
                    Thread.sleep(attempts);
                if (attempts > MAXATTEMPS)
                    return null;
                try {
                    numBytes += socketChannel.read(buffer);
                } catch (IOException e) {
                    return null;
                }
            }

            buffer.rewind();
            byte[] b = Arrays.copyOfRange(buffer.array(), 0, ii);

            return b;
        } catch (
                IllegalArgumentException e)

        {
            return null;

        }

    }


    public synchronized Peer getFreePeer() {
        long attempts = 0;

        // TODO некрасиво
        Collections.sort(peers, (o1, o2) -> {
            if (o1.goodPacket != o2.goodPacket)
                return (int) (o2.goodPacket - o1.goodPacket);
            return (int) (o1.attempts - o2.attempts);
        });

        for (Peer p : peers) {
            if (!p.isBadPeer()) {

                if (!p.isUsed() && p.getLock().tryLock() == true) {
                    p.incAttepmpts();
                    p.setUsed(true);
                    return p;
                }

            }
        }

        return null;
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
            Long totalLength = new Long(0);

            for (int i = 0; i < files.size(); i++) {
                BeDictionary befile = (BeDictionary) files.get(i);
                Long fileLength = befile.get("length").getLong();
                totalLength += fileLength;
                String strFile = new String();

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


//        peers.add(new Peer(new InetSocketAddress("192.168.1.10", 40051)));
//        peers.add(new Peer(new InetSocketAddress("192.168.1.10", 49158)));

        Thread announcer = new Thread(new Announcer(this, (BeList) root.get("announce-list")));
        announcer.start();

        //      storage.checkAll();

        Updater updater = new Updater(this, 5);

        while (selector.keys().size() == 0) {
            Thread.sleep(1000);
        }

        downloadLoop(updater);


//        while (!storage.isStorageValid()) {
        // refreshPeers(url);

//              Downloader n = new Downloader(this, 0);
///             n.run();

        //if (storage.isValidPiece(i)) continue;
//        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
//        ArrayList<Runnable> ar = new ArrayList<>();
//        for (int i = 0; i < 1; i++) {
//            Downloader n = new Downloader(this, i);
//            queue.put(n);
//            ar.add(n);
//        }


//        ThreadPoolExecutor tpe = new ThreadPoolExecutor(40, 40, 1, TimeUnit.MILLISECONDS, queue);
//        tpe.prestartAllCoreThreads();
//        tpe.shutdown();
//        tpe.awaitTermination(5, TimeUnit.SECONDS);
//        long i = 0;

//        while (!storage.isStorageValid()) {
//
//            for (Runnable r : ar) {
//                Downloader s = (Downloader) r;
//                if (storage.getPieceIndex(s.p) != -1)
//                    System.out.format("%5s %4d %5d %22s - %6d - %6d %8d %s \n",
//                            s.peer.getPeerID().substring(0, 5),
//                            s.percentAviable(),
//                            s.peer.attempts,
//                            s.peer.getIsa(),
//                            storage.getPieceIndex(s.p),
//                            s.peer.getGoodPacket(),
//                            s.peer.getProcesDownload(),
//
//                            s.peer.s
//                    );
//            }
////                System.out.format( s.num + " - " + storage.getPieceIndex(s.p) + " " + s.s);
//            if (i % 360 == 0)
//                //refreshPeers(url);
//
//                Thread.sleep(5000);
//
// /*           ConsoleHelper.writeMessageLn("---------------------");
//            System.out.format("%4d %5d / %5d / %5d\n", i, storage.numValid(), storage.getNumPieces(), ar.size());*/
//            i++;
//        }
        ConsoleHelper.writeMessageLn("OK");

//            if (storage.isStorageValid()) ConsoleHelper.writeMessageLn("");
    }

}
