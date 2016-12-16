package com.dms.bittorent.bencoding;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by r2 on 21.11.2016.
 */
public class BeLong implements BeRec<Long> {

    public BeLong(String strFile, AtomicInteger index) {
        Integer indexL = strFile.indexOf('e',index.get());
        index.incrementAndGet();
        value = Long.parseLong(strFile.substring(index.get(),indexL));
        index.set(indexL);
    }

    private Long value;

    @Override
    public BeType getType() {
        return BeType.LONG;
    }

    @Override
    public long getLong() {
        return value;
    }

    @Override
    public List getList() {
        return null;
    }

    public Long getValue() {
        return value;
    }

    @Override
    public String beCode() {
        return "i" + value + "e";
    }
}
