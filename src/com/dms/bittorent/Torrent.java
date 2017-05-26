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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Created by r2 on 22.11.2016.
 */
public class Torrent {
    // TODO исправить
    final String PEER_ID = "-BT1042-7da4d9d07789";
    Storage storage;
    private byte[] info_hash = new byte[20];

    private ArrayList<Peer> peers = new ArrayList<>();

//    private ArrayList<InetSocketAddress> peers = new ArrayList<>();

    public ArrayList<Peer> getPeers() {
        return peers;
    }

    private void HTTPSendPeersRequist(String u) throws IOException {
        u = u + (u.contains("?") ? "&" : "?");
        StringBuilder url = new StringBuilder(u);
        url.append("info_hash=" + URLEncoder.encode(new String(info_hash, StandardCharsets.ISO_8859_1), String.valueOf(StandardCharsets.ISO_8859_1)))
                .append("&peer_id=" + PEER_ID)
                .append("&port=" + "6882")
                .append("&uploaded=" + "0")
                .append("&downloaded=" + "0")
                .append("&left=" + "123")
                .append("&compact=" + "1")
                .append("&no_peer_id=" + "0")
                .append("&event=" + "started");

        URL obj = new URL(url.toString());
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();

        if (responseCode == 200) {
            byte[] buffer = new byte[1024];
            try (InputStream in = con.getInputStream()) {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while (true) {
                    int readBytesCount = in.read(buffer);
                    if (readBytesCount == -1) {
                        break;
                    }
                    if (readBytesCount > 0) {
                        baos.write(buffer, 0, readBytesCount);
                    }
                }
                baos.flush();
                baos.close();
                byte[] data = baos.toByteArray();
                ArrayList<BeRec> resp = bencoding.parsefile2(new String(data, StandardCharsets.ISO_8859_1));
                if (((BeDictionary) resp.get(0)).get("peers") != null) {
                    String strPeers = (String) ((BeDictionary) resp.get(0)).get("peers").getValue();
                    toPeerList(strPeers.getBytes(StandardCharsets.ISO_8859_1));
                }

            }
        }
    }

    private void UDPSendPeersRequist(String u) throws IOException {
        URI uri = URI.create(u);
        final int BASE_TIMEOUT_SECONDS = 1;
        final int PACKET_LENGTH = 512;

        InetSocketAddress inetSocketAddress = new InetSocketAddress("192.168.1.10", 6882);
        InetSocketAddress isa = new InetSocketAddress(uri.getHost(), uri.getPort());
//        System.out.println(isa);

        int maxAttempts = 3;
        DatagramSocket socket = new DatagramSocket();
        socket.connect(isa);
        int transactionalId = new Random().nextInt();
        Long connectionId = Long.valueOf(0);

        ByteBuffer requestByte = UDPMessages.getByteRequest(transactionalId);
        int attempt = 1;
        while (attempt < maxAttempts) {
            if (connectionId == 0) {
                try {
                    socket.send(new DatagramPacket(requestByte.array(), requestByte.capacity(), isa));

                    int timeout = BASE_TIMEOUT_SECONDS * (int) Math.pow(2, attempt);
                    socket.setSoTimeout(timeout * 1000);

                    DatagramPacket p = new DatagramPacket(new byte[PACKET_LENGTH], PACKET_LENGTH);
                    socket.receive(p);

                    ByteBuffer bb = ByteBuffer.wrap(p.getData(), 0, p.getLength());
                    if (UDPMessages.readMessage(bb) == UDPMessages.UDPMessageType.CONNECT_RESPONSE) {
                        if (bb.getInt() == transactionalId) {
                            connectionId = bb.getLong();
                            attempt = 0;
                        }
                    } else {
                        System.out.println("error");
                        attempt = maxAttempts;
                    }

                } catch (SocketTimeoutException ste) {
                }
            } else {
                ByteBuffer anounceByte = UDPMessages.getByteAnounce(transactionalId, connectionId, PEER_ID, info_hash);
                socket.send(new DatagramPacket(anounceByte.array(), anounceByte.capacity(), isa));
                int timeout = BASE_TIMEOUT_SECONDS * (int) Math.pow(2, attempt);
                socket.setSoTimeout(timeout * 1000);

                DatagramPacket p = new DatagramPacket(new byte[PACKET_LENGTH], PACKET_LENGTH);
                socket.receive(p);

                ByteBuffer bb = ByteBuffer.wrap(p.getData(), 0, p.getLength());
                if (UDPMessages.readMessage(bb) == UDPMessages.UDPMessageType.ANNOUNCE_RESPONSE) {
                    if (bb.getInt() == transactionalId) {
                    }

                    int interval = bb.getInt();
                    int incomplete = bb.getInt();
                    int complete = bb.getInt();

                    while (bb.remaining() > 5) {
                        byte[] ipBytes = new byte[6];
                        bb.get(ipBytes);
                        toPeerList(ipBytes);

                    }


                }
            }

            attempt++;
        }

    }

    private void toPeerList(byte[] data) {
        ByteBuffer peers = ByteBuffer.wrap(data);

        for (int i = 0; i < data.length / 6; i++) {
            byte[] ipBytes = new byte[4];
            peers.get(ipBytes);
            InetAddress ip = null;
            try {
                ip = InetAddress.getByAddress(ipBytes);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            int port =
                    (0xFF & (int) peers.get()) << 8 |
                            (0xFF & (int) peers.get());

            Peer isa = new Peer(new InetSocketAddress(ip, port));

            if (!this.peers.contains(isa)) this.peers.add(isa);


        }

    }


    public SocketChannel makeHandShake(Peer peer) throws BadPeer, BadHandshake, InterruptedException, BadPiece {
        InetSocketAddress isa = peer.getIsa();
        peer.BitField = new BitSet();
        final int HS_LENGTH = 49;
        final String PROTOCOL_ID = "BitTorrent protocol";
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(new InetSocketAddress(peer.getIsa().getAddress(), peer.getIsa().getPort()));

            Selector sel = Selector.open();
            channel.register(sel, SelectionKey.OP_CONNECT);
            boolean isHandShake = false;

            while (true) {
                if (sel.isOpen()) {
                    int keys = sel.select();
                    if (keys > 0) {
                        Set<SelectionKey> selectedKeys = sel.selectedKeys();
                        for (SelectionKey sk : selectedKeys) {
                            if (!sk.isValid()) {
                                break;
                            }
                            if (sk.isConnectable()) {
                                System.out.println("accepting");
                                channel.finishConnect();
                                channel.register(sel, SelectionKey.OP_WRITE);
                                sk.interestOps(SelectionKey.OP_WRITE);
                            }
                            if (sk.isWritable()) {
                                SocketChannel ch = (SocketChannel) sk.channel();
                                System.out.println("writing");
                                if (!isHandShake)
                                    ch.write(HTTPMessages.getHandShakeMessage(PEER_ID, info_hash));

                                System.out.println("total wrote: ");
                                sk.interestOps(SelectionKey.OP_READ);
                            }
                            if (sk.isReadable()) {
                                SocketChannel ch = (SocketChannel) sk.channel();

                                if (!isHandShake) {
                                    isHandShake = checkHandShake(ch, info_hash, peer);
                                }
                                if (isHandShake) {
                                    byte[] m = readMessage(channel);
                                }
                                System.out.println("total wrote: ");
                                sk.interestOps(SelectionKey.OP_READ);
                            }

                        }
                    }
                }
            }


        } catch (IOException e) {
            System.out.println(e.fillInStackTrace());
        }
        return null;
    }

    boolean checkHandShake(SocketChannel ch, byte[] info_hash, Peer peer) throws IOException, BadHandshake {

        ByteBuffer buffer = ByteBuffer.allocate(1);
        ch.read(buffer);

        int headerStr = buffer.get(0);

        buffer = ByteBuffer.allocate(HTTPMessages.HS_LENGTH + headerStr);
        ch.read(buffer);
        buffer.rewind();

        byte[] pstr = new byte[headerStr];
        buffer.get(pstr);

        if (!(new String(pstr, StandardCharsets.ISO_8859_1)).contains(HTTPMessages.PROTOCOL_ID))
            throw new BadHandshake();

        buffer.get(new byte[8]);
        byte[] infoHashR = new byte[20];
        buffer.get(infoHashR);
        buffer.get(peer.peerID);

        if (!Arrays.equals(infoHashR, info_hash))
            throw new BadHandshake();
        return true;
    }

    /*    try {
            channel.write(HTTPMessages.getHandShakeMessage(PEER_ID, info_hash));

            ByteBuffer len = ByteBuffer.allocate(1);
            ByteBuffer data;

            if (channel.read(len) < len.capacity()) {
                return null;
            }
            len.rewind();
            int pstrlen = len.get();

            data = ByteBuffer.allocate(HS_LENGTH + pstrlen);
            data.put((byte) pstrlen);
            channel.read(data);

            data.rewind();

            pstrlen = Byte.valueOf(data.get()).intValue();
            byte[] pstr = new byte[pstrlen];
            data.get(pstr);

            data.get(new byte[8]);

            byte[] infoHashR = new byte[20];
            data.get(infoHashR);

            data.get(peer.peerID);


            if (!new String(pstr, StandardCharsets.ISO_8859_1).equals(PROTOCOL_ID)) throw new BadHandshake();

            channel.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.INTERESTED));
            //channel.write(HTTPMessages.getByteMessage(HTTPMessages.HTTPMessageType.UNCHOKE));
            Integer attempts = Integer.valueOf(3);
            Thread.sleep(200);


            do {
                attempts--;
                byte[] m = readMessage(channel);
                HTTPMessages.HTTPMessageType mt = HTTPMessages.readMessage(m);
                if (mt == HTTPMessages.HTTPMessageType.BITFIELD) {
                    updateBitField(peer, m);
                }

                if (mt == HTTPMessages.HTTPMessageType.HAVE) {
                    updateHAVE(peer, m);
                }

                if (mt == HTTPMessages.HTTPMessageType.UNCHOKE) return channel;
                if (mt == HTTPMessages.HTTPMessageType.HAVE) attempts++;
            } while (attempts != 0);
            channel.close();
        } catch (IOException e) {

        }
        return null;*/


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
        peer.setBitField(i);
    }


    public byte[] downloadPiece(Piece piece, SocketChannel channel, Peer peer) throws Exception {
        peer.setProcesDownload(-5);
        final int PACKET_SIZE = 16384;
        byte[] res = new byte[storage.getPieceSize(piece)];
        byte[] byteMessage;
        HTTPMessages.HTTPMessageType mt;
        int pl = storage.getPieceSize(piece);
        int j = 0;
        int numPiece = storage.getPieceIndex(piece);
        peer.setProcesDownload(-4);
        while (j < pl) {
            long start = System.currentTimeMillis();
            int questSize = (pl - j > PACKET_SIZE) ? PACKET_SIZE : pl - j;
            peer.setProcesDownload(-3);

            channel.write(HTTPMessages.getByteRequest1(numPiece, j, questSize));
            peer.setProcesDownload(-2);
            peer.setProcesDownload(j);
            int count = 100;

            do {
                byteMessage = readMessage(channel);
                mt = HTTPMessages.readMessage(byteMessage);

                if (mt == HTTPMessages.HTTPMessageType.HAVE) {
                    updateHAVE(peer, byteMessage);
                }

                if (mt == HTTPMessages.HTTPMessageType.CHOKE) {
                    do {
                        Thread.sleep(1000);
                        byteMessage = readMessage(channel);
                        mt = HTTPMessages.readMessage(byteMessage);
                        count--;
                        long finish = System.currentTimeMillis();
                        long timeConsumedMillis = finish - start;
                        if (timeConsumedMillis > 2000) throw new BadPeer();

                    } while (mt != HTTPMessages.HTTPMessageType.UNCHOKE && count > 0);
                    channel.write(HTTPMessages.getByteRequest1(numPiece, j, questSize));
//                    throw new BadPiece();
                }

                count--;
            } while (mt != HTTPMessages.HTTPMessageType.PIECE && count > 0);

            if (count == 0) {
                throw new BadPiece();
            }

            byte[] requestData = Arrays.copyOfRange(byteMessage, 9, byteMessage.length);
            System.arraycopy(requestData, 0, res, j, questSize);
            j += questSize;
            long finish = System.currentTimeMillis();
            long timeConsumedMillis = finish - start;
            if (timeConsumedMillis > 2000) throw new BadPeer();

        }


        return res;


    }

    private byte[] readMessage(SocketChannel socketChannel) throws IOException, BadPiece {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        socketChannel.read(buffer);
        buffer.rewind();
        int ii = buffer.getInt();
        if (ii > 20000 * 5) throw new BadPiece();
        buffer = ByteBuffer.allocate(ii);
        int i = socketChannel.read(buffer);
        //TODO разобраться
        while (ii > i && i > 0) {
            int numBytes = 0;
            if (socketChannel.isConnected())
                numBytes = socketChannel.read(buffer);
            if (numBytes <= 0) throw new BadPiece();
            i += numBytes;
        }

        if (i < 0) throw new BadPiece();

        buffer.rewind();
        //System.out.println(ii);

        byte[] b = Arrays.copyOfRange(buffer.array(), 0, ii);

        return b;
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
                if (p.getLock().tryLock() == true) {
                    p.incAttepmpts();
                    return p;
                }

            }
        }

        return null;
    }

    void refreshPeers(BeList url) {
        for (BeRec bl : url) {
            String urls = bl.getList().get(0).toString();
            try {
                if (urls.startsWith("http")) {
                    HTTPSendPeersRequist(urls);
                } else if (urls.startsWith("udp")) {
                    UDPSendPeersRequist(urls);
                }
            } catch (IOException e) {

            }
        }
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
        this.info_hash = ConsoleHelper.hash(info.beCode().getBytes(StandardCharsets.ISO_8859_1));

        String hash = (String) info.get("pieces").getValue();

        for (int i = 0; i < (hash.length() / 20); i++) {
            storage.addPiece(hash.substring(i * 20, (i + 1) * 20).getBytes(StandardCharsets.ISO_8859_1));
        }
        storage.setPieceLength(pieceLength);


        peers.add(new Peer(new InetSocketAddress("192.168.1.10", 40051)));

        //      storage.checkAll();
//        while (!storage.isStorageValid()) {
        BeList url = (BeList) root.get("announce-list");
        // refreshPeers(url);

//              Downloader n = new Downloader(this, 0);
///             n.run();

        //if (storage.isValidPiece(i)) continue;
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
        ArrayList<Runnable> ar = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Downloader n = new Downloader(this, i);
            queue.put(n);
            ar.add(n);
        }


        ThreadPoolExecutor tpe = new ThreadPoolExecutor(40, 40, 1, TimeUnit.MILLISECONDS, queue);
        tpe.prestartAllCoreThreads();
        tpe.shutdown();
        tpe.awaitTermination(5, TimeUnit.SECONDS);
        long i = 0;

        while (!storage.isStorageValid()) {

            for (Runnable r : ar) {
                Downloader s = (Downloader) r;
                if (storage.getPieceIndex(s.p) != -1)
                    System.out.format("%5s %4d %5d %22s - %6d - %6d %8d %s \n",
                            s.peer.getPeerID().substring(0, 5),
                            s.percentAviable(),
                            s.peer.attempts,
                            s.peer.getIsa(),
                            storage.getPieceIndex(s.p),
                            s.peer.getGoodPacket(),
                            s.peer.getProcesDownload(),

                            s.peer.s
                    );
            }
//                System.out.format( s.num + " - " + storage.getPieceIndex(s.p) + " " + s.s);
            if (i % 360 == 0)
                //refreshPeers(url);

                Thread.sleep(5000);

            ConsoleHelper.writeMessageLn("---------------------");
            System.out.format("%4d %5d / %5d / %5d\n", i, storage.numValid(), storage.getNumPieces(), ar.size());
            i++;
        }
        ConsoleHelper.writeMessageLn("OK");

//            if (storage.isStorageValid()) ConsoleHelper.writeMessageLn("");
    }

}
