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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

/**
 * @author Rüdiger Gleim
 */
public class SQLTupelIterator implements Iterator<String[]> {

    private static Logger logger = LogManager.getLogger(SQLTupelIterator.class);

    protected static int BUFFERSIZE = 32;

    protected BufferedReader reader;
    protected String line;
    protected int seekPos;
    protected String[] buffer = new String[BUFFERSIZE];
    protected int bufferSize;
    protected boolean hasMore;

    public SQLTupelIterator(InputStream pInputStream, String pEncoding) throws IOException {
        reader = new BufferedReader(new InputStreamReader(pInputStream, pEncoding));
        line = null;
        seekPos = 0;
        hasMore = locateNext();
    }

    private boolean locateNext() throws IOException {
        if (reader == null) return false;
        int lCurrentHitPos = -1;
        do {
            // Fetch a line to work on
            if (line == null) {
                do {
                    line = reader.readLine();
                    if (line == null) {
                        reader.close();
                        reader = null;
                        return false;
                    }
                } while (!line.startsWith("INSERT INTO "));
                seekPos = 0;
            }
            lCurrentHitPos = line.indexOf('(', seekPos);
            if (lCurrentHitPos == -1) line = null;
        } while (lCurrentHitPos == -1);
        int lCurrentFieldStart = ++lCurrentHitPos; // now first char in first field
        bufferSize = 0;
        boolean lInString = false;
        boolean lEscaped = false;
        boolean lIsString = false;
        boolean lContainsEscapes = false;
        do {
            switch (line.charAt(lCurrentHitPos)) {
                case '\\': {
                    if (lInString) {
                        lEscaped = !lEscaped;
                        lContainsEscapes = true;
                    }
                }
                case ',': {
                    if (!lInString) {
                        String lEntry = line.substring(lCurrentFieldStart, lCurrentHitPos);
                        buffer[bufferSize++] = lIsString ? (lEntry.length()==2 ? "" : (lContainsEscapes ? lEntry.substring(1, lEntry.length()-1).replace("\\'","'").replace("\\\"","\"").replace("\\\\","\\") : lEntry.substring(1, lEntry.length()-1))) : lEntry;
                        lCurrentFieldStart = lCurrentHitPos+1;
                        lIsString = false;
                        lContainsEscapes = false;
                    }
                    break;
                }
                case '\'': {
                    if (!lEscaped) {
                        lInString = lCurrentFieldStart == lCurrentHitPos;
                        lIsString = true;
                    }
                    break;
                }
                case ')': {
                    if (!lInString) {
                        String lEntry = line.substring(lCurrentFieldStart, lCurrentHitPos);
                        buffer[bufferSize++] = lIsString ? (lEntry.length()==2 ? "" : (lContainsEscapes ? lEntry.substring(1, lEntry.length()-1).replace("\\'","'").replace("\\\"","\"").replace("\\\\","\\") : lEntry.substring(1, lEntry.length()-1))) : lEntry;
                        lCurrentFieldStart = lCurrentHitPos+1;
                        seekPos = lCurrentHitPos+1;
                        lIsString = false;
                        lContainsEscapes = false;
                        return true;
                    }
                    break;
                }
            }
            if (line.charAt(lCurrentHitPos) != '\\' && lEscaped) {
                lEscaped = false;
            }
            lCurrentHitPos++;
        } while (true);
    }

    @Override
    public boolean hasNext() {
        return hasMore;
    }

    @Override
    public String[] next() {
        if (!hasMore) return null;
        String[] lResult = new String[bufferSize];
        for (int i=0; i<bufferSize; i++) {
            lResult[i] = buffer[i];
        }
        try {
            hasMore = locateNext();
        }
        catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return lResult;
    }

    @Override
    public void remove() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}