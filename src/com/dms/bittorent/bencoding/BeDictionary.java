package com.dms.bittorent.bencoding;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by r2 on 21.11.2016.
 */
public class BeDictionary extends LinkedHashMap<BeString, BeRec> implements BeRec<LinkedHashMap> {


    @Override
    public LinkedHashMap<BeString, BeRec> getValue() {
        return this;
    }

    public BeRec get(String strKey)
    {
        for(Map.Entry<BeString,BeRec> e:this.entrySet())
            if (e.getKey().equals(strKey)) return e.getValue();
        return null;
    }

    public BeDictionary(String strFile, AtomicInteger index) {
        while (strFile.length() > index.get()) {
            index.incrementAndGet();
            Character ch = strFile.charAt(index.get());
            if (ch == 'e') return;
            BeString key = new BeString(strFile, index);
            index.incrementAndGet();
            BeRec el = bencoding.getElem(strFile, index);
            put(key, el);
        }
    }

    public String beCode()
    {

        String res = "d";
        for(Map.Entry <BeString, BeRec> e:this.entrySet() )
        {
            res = res + e.getKey().beCode() + e.getValue().beCode();
        }

        return res + "e";
    }

    @Override
    public BeType getType() {
        return BeType.DICTIONARY;
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
