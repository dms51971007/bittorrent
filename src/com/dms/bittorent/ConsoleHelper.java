package com.dms.bittorent;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by r2 on 12.10.2016.
 */
public class ConsoleHelper {

    public static void writeError(Exception e) {
        System.out.println("Ошибка: " + e.getMessage());
    }


    public static String fromBeToUTF(String str)
    {
        return new String(str.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
    }

    public static void writeMessage(String message) {
        System.out.print(message);
    }


    public static void writeMessageLn(String message) {
        System.out.println(message);
    }

    public static byte[] hash(byte[] data) {
        MessageDigest crypt;
        try {
            crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(data);
            return crypt.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }



    public static String readString() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));) {
            return reader.readLine();
        }

    }

    ;

}

