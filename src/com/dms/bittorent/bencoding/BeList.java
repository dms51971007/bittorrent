package com.dms.bittorent.bencoding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by r2 on 21.11.2016.
 */
public class BeList extends ArrayList<BeRec> implements BeRec {
    @Override
    public ArrayList<BeRec> getValue() {
        return this;
    }

    @Override
    public String beCode() {
        String res = "l";
        for(BeRec e:this )
        {
            res = res + e.beCode();
        }

        return res + "e";

    }

    public BeList(String strFile, AtomicInteger index) {
        while (strFile.length() > index.get()) {
            index.incrementAndGet();
            Character ch = strFile.charAt(index.get());
            if (ch == 'e') return;
            add(bencoding.getElem(strFile, index));

        }
    }

    @Override
    public BeType getType() {
        return BeType.LIST;
    }

    @Override
    public List getList() {
        return getValue();
    }

    @Override
    public long getLong() {
        return 0;
    }


}
