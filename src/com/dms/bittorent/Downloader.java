package com.dms.bittorent;

import java.util.BitSet;

/**
 * Created by r2 on 02.12.2016.
 */
public class Downloader implements Runnable {
    Torrent torrent;
    Integer num;
    Piece p;
    Peer peer;

    public Downloader(Torrent torrent, Integer num) {
        this.torrent = torrent;
        this.num = num;
    }

    public int percentAviable() {
        BitSet bs = peer.getRecieveBitField();
        int res = 0;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            res++;
        }

        return (res * 100 / torrent.storage.getNumPieces());
    }

    @Override
    public void run() {

/*        while (torrent.getPeers().size() == 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        while (!torrent.storage.isStorageValid()) {

            peer = torrent.getFreePeer();
            if (peer != null)
                try {
                    try {
                        try (SocketChannel socketChannel = torrent.makeHandShake(peer);)  {

                            if (socketChannel != null) {
                                peer.s= new StringBuilder("H");
                                while ((p = torrent.storage.getFreePiece(peer.BitField)) != null) {
                                    try {
                                        peer.s.append("p");

                                        byte[] pp;
                                        pp = torrent.downloadPiece(p, socketChannel, peer);
                                            peer.s.append("d");

                                        if (torrent.storage.checkPiece(p, pp)) {
                                            torrent.storage.writePiece(p, pp);
                                            //peer.s.append('+');
                                            peer.s.append("+");
                                            peer.incGoodPacket();
//                                                ConsoleHelper.writeMessageLn("" +  num +" - " +torrent.storage.getPieceIndex(p).toString());
                                        } else throw new Exception();
                                    } finally {
                                        p.getLock().unlock();
                                        p = null;

                                    }
                                }
                               // System.out.println(peer.toString() + " no free pieces " );
                                peer.resetGoodPacket();
                            }
                        }
                    } catch (BadPiece e) {
                        peer.resetGoodPacket();
//                        peer.s.append('@');
                    } catch (BadPeer e) {
                        peer.resetGoodPacket();
  //                      peer.s.append('&');
//                    peer.setBadPeer();
                    } finally {
                        peer.getLock().unlock();
                        peer = null;
                    }

                } catch (Exception e) {
//                peer.s.append('*');

                }
//            peer.s.append('!');

            if (torrent.storage.numValid()-torrent.storage.getNumPieces() == -1) break;
            try {
                //break;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        System.out.print("|" + num);*/
    }
}
