package com.dms.bittorent;

import com.dms.bittorent.bencoding.BeDictionary;
import com.dms.bittorent.bencoding.BeList;
import com.dms.bittorent.bencoding.BeRec;
import com.dms.bittorent.bencoding.bencoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by r2 on 31.05.2017.
 */
public class Announcer implements Runnable {
    Torrent torrent;
    BeList url;

    public Announcer(Torrent torrent, BeList url) {
        this.torrent = torrent;
        this.url = url;
    }

    private void toPeerList(byte[] data) {
        Set<Peer> addPeer = new HashSet<>();

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

            Peer isa = new Peer(new InetSocketAddress(ip, port),torrent.storage.getNumOfPieces());

            if (!torrent.getPeers().contains(isa)) {
                System.out.print("-");


                if (torrent.peerLock.tryLock())
                    try {
                        torrent.getPeers().add(isa);
                    } finally {
                        torrent.peerLock.unlock();
                    }


            }
        }


    }

    private void HTTPSendPeersRequist(String u) throws IOException {
        u = u + (u.contains("?") ? "&" : "?");
        StringBuilder url = new StringBuilder(u);
        url.append("info_hash=" + URLEncoder.encode(new String(torrent.getInfoHash(), StandardCharsets.ISO_8859_1), String.valueOf(StandardCharsets.ISO_8859_1)))
                .append("&peer_id=" + torrent.PEER_ID)
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
                try {
                    ArrayList<BeRec> resp = bencoding.parsefile2(new String(data, StandardCharsets.ISO_8859_1));
                    if (((BeDictionary) resp.get(0)).get("peers") != null) {
                        String strPeers = (String) ((BeDictionary) resp.get(0)).get("peers").getValue();
                        toPeerList(strPeers.getBytes(StandardCharsets.ISO_8859_1));
                    }
                } catch (NumberFormatException e) {
                }

            }
        }
    }

    private void UDPSendPeersRequist(String u) throws IOException {
        URI uri = URI.create(u);
        final int BASE_TIMEOUT_SECONDS = 1;
        final int PACKET_LENGTH = 512;

        InetSocketAddress inetSocketAddress = new InetSocketAddress("192.168.1.10", 6882);
        InetSocketAddress isa=null;
        try {
            isa = new InetSocketAddress(uri.getHost(), uri.getPort());
        } catch (IllegalArgumentException e)
        {
            e.printStackTrace();
            return;
        }
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
                ByteBuffer anounceByte = UDPMessages.getByteAnounce(transactionalId, connectionId, torrent.PEER_ID, torrent.getInfoHash());
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


    void refreshPeers(BeList url) {
        for (BeRec bl : url) {
            String urls = bl.getList().get(0).toString();
            System.out.println(urls);
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

   /* void fillSelectorsWithPeer() {

        int numPeers = 2;
        Peer peer;


        while (((peer = torrent.getFreePeer()) != null) && numPeers > 0) {
            SocketChannel channel = null;
            try {
                channel = SocketChannel.open();

                if (channel.connect(new InetSocketAddress(peer.getIsa().getAddress(), peer.getIsa().getPort()))) {
                    channel.configureBlocking(false);
                    int ops = channel.validOps();
                    channel.register(torrent.selector, ops, peer);
                    numPeers--;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }*/


    @Override
    public void run() {
        boolean stop = false;
        while (!stop) {

            refreshPeers(url);
            //fillSelectorsWithPeer();

            try {
                Thread.sleep(30 * 60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
