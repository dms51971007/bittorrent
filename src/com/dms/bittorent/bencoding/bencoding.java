package com.dms.bittorent.bencoding;

import com.dms.bittorent.ConsoleHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by r2 on 21.11.2016.
 */
public class bencoding {

    private ArrayList<BeRec> elements;

    public ArrayList<BeRec> getElements() {
        return elements;
    }

    public static ArrayList<BeRec> parsefile2(String strFile)
    {
        ArrayList <BeRec> res = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);
        while (strFile.length() > index.get()) {
            res.add(getElem(strFile,index));
            index.incrementAndGet();
        }
        return res;
    }

    static BeRec getElem(String strFile, AtomicInteger index) {
            Character ch = strFile.charAt(index.get());
            if (ch == 'd') {
                return new BeDictionary(strFile, index);
            } else if (ch == 'l') {
                return new BeList(strFile, index);
            } else if (ch == 'i') {
                return new BeLong(strFile, index);
            } else if ("0123456789".contains(ch.toString())) { return new BeString(strFile, index);
            }
        return null;
    }


    public bencoding(String file) {
        String fileContent = null;
        try {

            fileContent = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.ISO_8859_1);

        } catch (IOException e) {
            e.printStackTrace();
        }
         elements = parsefile2(fileContent);
    }



}
