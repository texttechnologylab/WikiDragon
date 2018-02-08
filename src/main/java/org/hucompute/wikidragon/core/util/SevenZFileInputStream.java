/*
 * Copyright 2018
 * Text-Technology Lab
 * Johann Wolfgang Goethe-Universität Frankfurt am Main
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/agpl-3.0.en.html.
 */

package org.hucompute.wikidragon.core.util;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Rüdiger Gleim
 */
public class SevenZFileInputStream extends InputStream {

    private static int BUFFER_SIZE = 134217728; // 128MB

    private SevenZFile sevenZFile;
    private long remainingLength;
    private byte buffer[] = new byte[BUFFER_SIZE];
    private int bufferOffset = 0;
    private int bufferLen = 0;

    public SevenZFileInputStream(File pFile) throws IOException {
        sevenZFile = new SevenZFile(pFile);
        SevenZArchiveEntry entry = sevenZFile.getNextEntry();
        if (entry!=null){
            remainingLength = entry.getSize();
        }
    }

    private void fetchData() throws IOException {
        bufferLen = sevenZFile.read(buffer, 0, remainingLength > BUFFER_SIZE ? BUFFER_SIZE : (int)remainingLength);
        remainingLength -= bufferLen;
        bufferOffset = 0;
    }

    @Override
    public int read() throws IOException {
        if (bufferOffset >= bufferLen) {
            if (remainingLength > 0) {
                fetchData();
            }
            else {
                return -1;
            }
        }
        return buffer[bufferOffset++];
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (b.length == 0) return 0;
        if (bufferOffset >= bufferLen) {
            if (remainingLength > 0) {
                fetchData();
            }
            else {
                return -1;
            }
        }
        int lRead = 0;
        for (int i=bufferOffset; i<bufferLen; i++) {
            b[lRead++] = buffer[i];
            if (lRead >= b.length) break;
        }
        bufferOffset += lRead;
        return lRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b.length == 0) return 0;
        if (len == 0) return 0;
        if (bufferOffset >= bufferLen) {
            if (remainingLength > 0) {
                fetchData();
            }
            else {
                return -1;
            }
        }
        int lRead = 0;
        for (int i=bufferOffset; i<bufferLen; i++) {
            b[off + (lRead++)] = buffer[i];
            if ((off + lRead >= b.length) || (lRead >= len)) break;
        }
        bufferOffset += lRead;
        return lRead;
    }

    @Override
    public long skip(long n) throws IOException {
        return super.skip(n);
    }

    @Override
    public int available() throws IOException {
        if (remainingLength == 0) return 0;
        return bufferLen-bufferOffset;
    }

    @Override
    public void close() throws IOException {
        sevenZFile.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
    }

    @Override
    public boolean markSupported() {
        return super.markSupported();
    }
}
