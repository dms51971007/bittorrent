package com.dms.bittorent;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by r2 on 27.11.2016.
 */
public class Storage {
    private ArrayList<Piece> pieces = new ArrayList<>();
    private LinkedHashMap<Path, Long> files = new LinkedHashMap<>();
    private Path dir;
    private Long pieceLength;
    private Long pieceLengthLast;
    private Long length;

    public Long getLength() {
        return length;
    }

    public synchronized Piece getFreePiece(BitSet bitfield) {
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            if (!p.isValid() && bitfield.get(i))
                if (p.getLock().tryLock() == true) return p;
        }
        return null;
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



    public int numValid() {
        int res = 0;
        for (Piece p : pieces)
            if (p.isValid()) {
                res++;
            }
        return res;
    }


    public int getNumPieces() {
        return pieces.size();
    }

    public void setPieceLength(Long pieceLength) {
        this.pieceLength = pieceLength;
        pieceLengthLast = (this.length - (long) (pieces.size() - 1) * pieceLength);
    }

    public void addFile(String strFile, Long length) {
        //TODO проверка на повторный файл
        Path file = Paths.get(dir.toString() + "/" + strFile);
        files.put(file, length);
    }

    public void addPiece(byte[] hash) {
        pieces.add(new Piece(hash));
    }

    public byte[] readPiece(Piece p) throws IOException {
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

        return res;
    }

    public void writePiece(Piece p, byte[] piece) throws Exception {

        int pieceLength = getPieceSize(p);

        if (pieceLength != piece.length) {
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
            raf.write(Arrays.copyOfRange(piece, (int) ft.piecePos, (int) (ft.piecePos + ft.size)));
            raf.close();
        }
        p.setValid(true);

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
                ConsoleHelper.writeMessageLn(String.format("Strorage checking (valid/checked/toatal): %d/%d/%d", valid, i, pieces.size() - 1));
            }
            byte[] b = readPiece(pieces.get(i));
            if (checkPiece(pieces.get(i), b)) {
                pieces.get(i).setValid(true);
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

