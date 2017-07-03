package com.dms.bittorent;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


/**
 * Created by r2 on 19.06.2017.
 */
public class Logger {
    final String LOG_DIRECTORY = "c:/log/";
    OutputStream out;

    public Logger(String fileName) {

        Path p = Paths.get(LOG_DIRECTORY + fileName);
        try {
            out = new BufferedOutputStream(Files.newOutputStream(p, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        out.close();
    }
}
