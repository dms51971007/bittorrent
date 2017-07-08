package com.dms.bittorent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by r2 on 27.11.2016.
 */
public class Storage implements Runnable {
    private ArrayList<Piece> pieces = new ArrayList<>();
    private LinkedHashMap<Path, Long> files = new LinkedHashMap<>();
    private Path dir;
    private Long pieceLength;
    private Long pieceLengthLast;
    private Long length;
    private ConcurrentHashMap<Piece, Peer> writeMap = new ConcurrentHashMap();
    private Stack<Piece> readCache = new Stack<>();

    private BitSet bitField;

    public BitSet getBitField() {
        return bitField;
    }

    public void setBitField() {
        this.bitField = new BitSet(pieces.size());
        for (int i = 0; i <= pieces.size() - 1; i++)
            if (pieces.get(i).isValid()) this.bitField.set(i);
    }

    public Long getLength() {
        return length;
    }

    public int getNumOfPieces() {
        return pieces.size();
    }


    public Integer getPieceIndex(Piece p) {
        return pieces.indexOf(p);
    }

    public void setLength(Long length) {
        this.length = length;
    }

    private ArrayList<FileTable> getPiece(int pieceNum) {

        ArrayList<FileTable> res = new ArrayList<FileTable>();
        Long posFileList = new Long(0);
        Long hashOffset = (long) pieceNum * this.pieceLength;
        Long pieceLength = (pieceNum == this.pieces.size()) ? this.pieceLength : this.pieceLengthLast;

        int i = 0;

        Path beginFile = null;
        Long beginPos = null;
        for (Map.Entry<Path, Long> fl : files.entrySet()) {
            Path file = fl.getKey();
            Long value = fl.getValue();

            if (beginFile == null && hashOffset <= value) {
                beginFile = file;
                beginPos = hashOffset;
                hashOffset = this.pieceLength;
            } else if (beginFile == null) {
                hashOffset -= value;
            }
            if (beginFile != null) {
                Long size = (hashOffset > value - beginPos) ? value - beginPos : hashOffset;
                res.add(new FileTable(file, beginPos, size, this.pieceLength - hashOffset));
                //System.out.println(String.format(" %d - %s - %d - %d", i, file, beginPos, size));
                hashOffset -= size;
            }
            i++;
            beginPos = Long.valueOf(0);
            if (hashOffset <= 0) break;
        }

        return res;
    }

    public Storage(String dir, String dirRoot) throws IOException {

        this.dir = Paths.get(dirRoot + "/" + dir + "/");
        Files.createDirectories(this.dir);
    }

    public int getPieceSize(Piece p) {
        int pieceNum = pieces.indexOf(p);
        return (!(pieceNum == this.pieces.size() - 1) ? this.pieceLength.intValue() : this.pieceLengthLast.intValue());
    }

    public boolean isValidPiece(int num) {
        return pieces.get(num).isValid();
    }

    public boolean isStorageValid() {
        for (Piece p : pieces)
            if (!p.isValid()) {
                return false;
            }
        return true;
    }

    public Piece getPieceByIndex(int pieceNumber) {
        return pieces.get(pieceNumber);
    }

    public int numValid() {
        int res = 0;
        for (Piece p : pieces)
            if (p.isValid()) {
                res++;
            }
        return res;
    }

    public Piece getFreePiece(BitSet bitfield) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            if (!p.isValid() && bitfield.get(i))
                if (!p.isUsed() && p.getLock().tryLock() == true) {
                    p.setUsed(true);
                    return p;
                }
        }
        return null;
    }

    public int getNumPieces() {
        return pieces.size();
    }

    public void setPieceLength(Long pieceLength) {
        this.pieceLength = pieceLength;
        this.pieceLengthLast = (this.length - (long) (pieces.size() - 1) * pieceLength);
    }

    public void addFile(String strFile, Long length) {
        //TODO проверка на повторный файл
        Path file = Paths.get(dir.toString() + "/" + strFile);
        files.put(file, length);
    }

    public void addPiece(byte[] hash) {
        pieces.add(new Piece(hash));
    }

    public byte[] readPieceCache(Piece p) throws IOException {
        if (p.getPieceData() == null) {
            return readPiece(p);
        }
        return p.getPieceData();
    }


    public synchronized byte[] readPiece(Piece p) throws IOException {
        //TODO проверять тольео нужные файлы
        int pieceNum = pieces.indexOf(p);
        checkAllFiles();
        byte[] res = new byte[getPieceSize(p)];
        for (FileTable ft : getPiece(pieceNum)) {

            RandomAccessFile raf = new RandomAccessFile(ft.file.toFile(), "r");
            byte[] b = new byte[(int) ft.size];
            raf.seek(ft.beginPos);
            raf.read(b);
            System.arraycopy(b, 0, res, (int) ft.piecePos, (int) ft.size);
            raf.close();
        }
        p.setPieceData(res);
        return p.getPieceData();
    }


    public void writePiece(Piece p, Peer peer) {
        writeMap.put(p, peer);
    }

    private void writeDisk(Piece p) throws Exception {
        int pieceLength = getPieceSize(p);

        if (pieceLength != p.getPieceData().length) {
            throw new Exception("Bad piece!!");
        }
        int pieceNum = pieces.indexOf(p);
        for (FileTable ft : getPiece(pieceNum)) {
            Files.createDirectories(ft.file.getParent());
            if (!Files.exists(ft.file)) Files.createFile(ft.file);

            RandomAccessFile raf = new RandomAccessFile(ft.file.toFile(), "rw");
            if (Files.size(ft.file) != files.get(ft.file)) {
                raf.setLength(files.get(ft.file));
            }
            raf.seek(ft.beginPos);
            raf.write(Arrays.copyOfRange(p.getPieceData(), (int) ft.piecePos, (int) (ft.piecePos + ft.size)));
            raf.close();
        }
        p.setValid(true);
//        p.setPieceData(null);

    }

    public void checkAllFiles() throws IOException {
        for (Map.Entry<Path, Long> entry : files.entrySet()) {
            Path pt = entry.getKey();
            Long sz = entry.getValue();

            Files.createDirectories(pt.getParent());

            if (!Files.exists(pt)) Files.createFile(pt);
            if (Files.size(pt) != sz) {
                RandomAccessFile raf = new RandomAccessFile(pt.toFile(), "rw");
                raf.setLength(sz);
                raf.close();
            }


            // now work with key and value...
        }
    }

    public boolean checkAll() throws IOException {
        boolean res = true;
        checkAllFiles();
        Integer persent = pieces.size() / 10;
        Integer valid = Integer.valueOf(0);
        for (int i = 0; i < pieces.size(); i++) {
            if (i % persent == 0 || i == pieces.size() - 1) {
                ConsoleHelper.writeMessageLn(String.format("Strorage checking (valid/checked/total): %d/%d/%d", valid, i, pieces.size() - 1));
            }
            byte[] b = readPiece(pieces.get(i));
            if (checkPiece(pieces.get(i), b)) {
                pieces.get(i).setValid(true);
                pieces.get(i).setPieceData(null);
                bitField.set(i);
                valid++;
            } else {
                pieces.get(i).setValid(false);
                res = false;
            }

        }
        return res;
    }

    public boolean checkPiece(Piece p, byte[] piece) {
        if (piece == null) return false;
        return Arrays.equals(ConsoleHelper.hash(piece), p.getHash());
    }


    public boolean checkPiece(Piece p) {
        if (p == null) return false;
        return Arrays.equals(ConsoleHelper.hash(p.getPieceData()), p.getHash());
    }

    @Override
    public void run() {
        while (true) {
            if (writeMap.size() == 0) continue;
            System.out.println("Cash size:" + writeMap.size());
            for (Map.Entry<Piece, Peer> e : writeMap.entrySet()) {
                try {
                    Peer peer = e.getValue();
                    Piece piece = e.getKey();
                    writeMap.remove(piece);
                    if (!checkPiece(piece)) {
                        System.out.println("BadPiece: " + peer);
                    } else {
                        bitField.set(getPieceIndex(piece));
                        peer.getSentBitField().set(getPieceIndex(piece));
                        writeDisk(piece);

                    }
                    piece.setUsed(false);
                    //piece.setPieceData(null);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }

            }
            for (Piece p :
                    pieces) {
                if (p.getLastReadTimer() > 3000 && p.isValid()) p.setPieceData(null);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class FileTable {
        Path file;
        long beginPos;
        long piecePos;
        long size;

        @Override
        public String toString() {
            return "FileTable{" +
                    "file=" + file +
                    ", beginPos=" + beginPos +
                    ", size=" + size +
                    ", piecePos=" + piecePos +
                    '}';
        }

        public FileTable(Path file, Long beginPos, Long size, Long piecePos) {
            this.file = file;
            this.beginPos = beginPos;
            this.size = size;
            this.piecePos = piecePos;

        }
    }
}

