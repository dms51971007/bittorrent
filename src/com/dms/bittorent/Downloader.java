package com.dms.bittorent;

import com.dms.bittorent.Exceptions.BadPeer;
import com.dms.bittorent.Exceptions.BadPiece;

import java.nio.channels.SocketChannel;
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
        BitSet bs = peer.BitField;
        int res = 0;
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            res++;
        }

        return (res * 100 / torrent.storage.getNumPieces());
    }

    @Override
    public void run() {

/*
        while (torrent.getPeers().size() == 0) {
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
                        //SocketChannel socketChannel = torrent.downloadLoop(peer);
                        peer.s = new StringBuilder("H");
                        while ((p = torrent.storage.getFreePiece(peer.BitField)) != null) {
                        }
                    } catch (BadPiece e) {
                    } catch (BadPeer e) {
                    } finally {
                    }

                } catch (
                        Exception e)
                {
                }

            if (torrent.storage.numValid() - torrent.storage.getNumPieces() == -1) break;
            try

            {
                //break;
                Thread.sleep(1000);
            } catch (
                    InterruptedException e)

            {
                e.printStackTrace();
            }

        }

        System.out.print("|" + num);
*/
    }
}
