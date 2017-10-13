package com.dms.bittorent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by r2 on 01.06.2017.
 */
public class Updater {
    private Torrent torrent;
    private long lastCall;
    private int interval;

    public Updater(Torrent torrent, int interval) {
        this.torrent = torrent;
        //this.lastCall = (new Date()).getTime() / 1000;
        this.interval = interval;
    }

    public void update() {

        if ((new Date()).getTime() / 1000 - lastCall > interval) {
            if (torrent.peerLock.tryLock()) {
                try {

                    long startTime = new Date().getTime();
                    this.lastCall = (new Date()).getTime() / 1000;

                    Set<SelectionKey> ss = torrent.selector.keys();
                    for (Iterator<SelectionKey> it = ss.iterator(); it.hasNext(); ) {

                        SelectionKey sk = it.next();
                        Peer p = (Peer) sk.attachment();
                        p.setUsed(true);
                        // if (p.isHandShake())
                        //System.out.println(p + " " + p.attempts);

                        if (p.fromLastLog() > 30 && !p.isHandShake()) {
//                            it.remove();
                            try {
                                sk.channel().close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            sk.cancel();
                            torrent.getPeers().remove(p);
                        } else if (p.fromLastLog() > 140 && p.isHandShake()) {
                            try {
                                sk.channel().close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            sk.cancel();
                            p.free();
                        } else if (!sk.isValid()) {
                            sk.interestOps(SelectionKey.OP_CONNECT);
                        }
                    }

                    for (Iterator<Peer> it = torrent.getPeers().iterator(); it.hasNext(); ) {
                        Peer p = it.next();
                        if (torrent.storage.isStorageValid()) {
                            it.remove();
                            continue;
                        }
                        System.out.println(p + " " + p.attempts);
                        if (!p.isUsed()) {
                            if (p.getBadHandShake() < 4) {
                                p.setSocketChannel(torrent.selector);
                            } else {
                                it.remove();
                                continue;
                            }
                        }
                        p.setUsed(false);

                    }


                    System.out.println("------------------------------");
                    System.out.println(torrent.storage.numValid() + " / " + torrent.storage.getNumPieces() + " / " + torrent.getPeers().size());

                    System.out.println("------------------------------");
                    System.out.println(" Total time:" + torrent.totalTime);

                    System.out.println(" Updater time: " + ((new Date()).getTime() - startTime));
                    torrent.totalTime = 0;
                } finally {
                    torrent.peerLock.unlock();
                }
            }
        }
    }
}
