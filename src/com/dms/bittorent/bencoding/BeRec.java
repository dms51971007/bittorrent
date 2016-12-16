package com.dms.bittorent.bencoding;

import java.util.List;

/**
 * Created by r2 on 21.11.2016.
 */
public interface BeRec <T> {
    T getValue();
    String beCode();
    BeType getType();
    long getLong();
    List getList();
}
