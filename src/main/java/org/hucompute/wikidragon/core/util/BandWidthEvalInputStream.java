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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Rüdiger Gleim
 */
public class BandWidthEvalInputStream extends InputStream {

    protected InputStream inputStream;

    protected long bytesRead;
    protected long bytesSkipped;
    protected long singleByteReads;
    protected long blockByteReads;
    protected long skipsCalled;

    protected long firstReadTimestamp;
    protected long latestReadTimestamp;

    public BandWidthEvalInputStream(InputStream pInputStream) {
        inputStream = pInputStream;
    }

    public long getBytesPerSecond() {
        long lDiv = (latestReadTimestamp-firstReadTimestamp)/1000;
        return lDiv == 0 ? 0 : bytesRead/lDiv;
    }

    public long getElapsedActivityTimeMS() {
        return latestReadTimestamp-firstReadTimestamp;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public long getBytesRead() {
        return bytesRead;
    }

    public long getBytesSkipped() {
        return bytesSkipped;
    }

    public long getSingleByteReads() {
        return singleByteReads;
    }

    public long getBlockByteReads() {
        return blockByteReads;
    }

    public long getSkipsCalled() {
        return skipsCalled;
    }

    public long getFirstReadTimestamp() {
        return firstReadTimestamp;
    }

    public long getLatestReadTimestamp() {
        return latestReadTimestamp;
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (firstReadTimestamp == 0) firstReadTimestamp = System.currentTimeMillis();
        int lRead = inputStream.read(b);
        if (lRead > 0) {
            bytesRead += lRead;
            blockByteReads++;
        }
        latestReadTimestamp = System.currentTimeMillis();
        return lRead;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (firstReadTimestamp == 0) firstReadTimestamp = System.currentTimeMillis();
        int lRead = inputStream.read(b, off, len);
        if (lRead > 0) {
            bytesRead += lRead;
            blockByteReads++;
        }
        latestReadTimestamp = System.currentTimeMillis();
        return lRead;
    }

    @Override
    public long skip(long n) throws IOException {
        if (firstReadTimestamp == 0) firstReadTimestamp = System.currentTimeMillis();
        long lSkipped = inputStream.skip(n);
        bytesSkipped += lSkipped;
        skipsCalled++;
        latestReadTimestamp = System.currentTimeMillis();
        return lSkipped;
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public int read() throws IOException {
        if (firstReadTimestamp == 0) firstReadTimestamp = System.currentTimeMillis();
        int lResult = read();
        if (lResult >= 0) {
            bytesRead++;
            singleByteReads++;
        }
        latestReadTimestamp = System.currentTimeMillis();
        return inputStream.read();
    }
}
