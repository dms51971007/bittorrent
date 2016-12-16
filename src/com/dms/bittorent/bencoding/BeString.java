package com.dms.bittorent.bencoding;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by r2 on 21.11.2016.
 */
public class BeString implements BeRec {
    String value;

    public BeString(String value) {
        this.value = value;
    }

    public boolean equals(String obj) {
        if (obj == null) return false;
        if (obj.equals(value)) return true;
        else return false;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String beCode() {
        return "" + value.length() + ":" + value;
    }

    public BeString(String strFile, AtomicInteger index) {

        Integer indexL = strFile.indexOf(':', index.get());
        Integer strLength = Integer.parseInt(strFile.substring(index.get(), indexL));
        value = strFile.substring(indexL + 1, indexL + strLength + 1);
//        System.out.println(value);
        index.set(strLength + indexL);

    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public BeType getType() {
        return BeType.STRING;
    }

    @Override
    public long getLong() {
        return 0;
    }

    @Override
    public List getList() {
        return null;
    }
}
