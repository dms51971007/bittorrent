package com.dms.bittorent;

import java.nio.file.Files;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by r2 on 27.11.2016.
 */
public class Piece {
    private byte[] hash;
    private boolean valid = true;


    private final Lock lock = new ReentrantLock();

    public Lock getLock() {
        return lock;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public byte[] getHash() {
        return hash;
    }

    public boolean isValid() {
        return valid;
    }



    public Piece(byte[] hash) {
        this.hash = hash;
        this.valid = false;
    }


}
