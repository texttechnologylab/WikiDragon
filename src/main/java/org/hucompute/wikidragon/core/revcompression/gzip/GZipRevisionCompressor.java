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

package org.hucompute.wikidragon.core.revcompression.gzip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressionTask;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

/**
 * @author Rüdiger Gleim
 */
public class GZipRevisionCompressor extends RevisionCompressor {

    private static Logger logger = LogManager.getLogger(GZipRevisionCompressor.class);

    protected int maxThreads;
    protected MediaWiki mediaWiki;

    protected Set<GZipCompressionRunnable> pool;
    protected List<RevisionCompressionTask> completedRevisionCompressionTasks;

    protected long uncompressedSum = 0;
    protected long compressedSum = 0;
    protected long compressedCount = 0;
    protected long uncompressedCount = 0;

    protected long writeCompletedRevisionsCounter;

    public GZipRevisionCompressor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()/2));
    }

    public GZipRevisionCompressor(int pMaxThreads) {
        mediaWiki = null;
        maxThreads = pMaxThreads;
        pool = new HashSet<>();
        completedRevisionCompressionTasks = new ArrayList<>();
        writeCompletedRevisionsCounter = 0;
    }

    @Override
    public void submitRevision(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException {
        submitRevisionWrap(pPage, pRevisionID, pParentId, pTimestamp, pUserName, pUserID, null, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
    }

    @Override
    public void submitRevision(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException {
        submitRevisionWrap(pPage, pRevisionID, pParentId, pTimestamp, null, NULLNODEID, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
    }

    public void submitRevisionWrap(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException {
        synchronized (pool) {
            if (mediaWiki == null) mediaWiki = pPage.getMediaWiki();
            // Prepare joint RevisionCompressionTask
            RevisionCompressionTask lRevisionCompressionTask;
            if (pUserID != NULLNODEID) {
                lRevisionCompressionTask = new RevisionCompressionTask(pPage, pRevisionID, pParentId, pTimestamp, pUserName, pUserID, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
            }
            else {
                lRevisionCompressionTask = new RevisionCompressionTask(pPage, pRevisionID, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
            }
            synchronized (pool) {
                while (pool.size() >= maxThreads) {
                    try {
                        pool.wait();
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                GZipCompressionRunnable lGZipCompressionRunnable = new GZipCompressionRunnable(this, lRevisionCompressionTask);
                pool.add(lGZipCompressionRunnable);
                new Thread(lGZipCompressionRunnable).start();
            }
            writeCompletedRevisions();
        }
    }

    private void writeCompletedRevisions() throws WikiDragonException {
        List<RevisionCompressionTask> lTasks;
        synchronized (completedRevisionCompressionTasks) {
            lTasks = new ArrayList<>(completedRevisionCompressionTasks);
            completedRevisionCompressionTasks.clear();
        }
        for (RevisionCompressionTask lRevisionCompressionTask:lTasks) {
            if (lRevisionCompressionTask.getUserID() != NULLNODEID) {
                fireRevisionCompressed(lRevisionCompressionTask.getPage(), lRevisionCompressionTask.getRevisionID(), lRevisionCompressionTask.getParentID(), lRevisionCompressionTask.getTimestamp(), lRevisionCompressionTask.getUserName(), lRevisionCompressionTask.getUserID(), lRevisionCompressionTask.getComment(), lRevisionCompressionTask.isMinor(), lRevisionCompressionTask.getModel(), lRevisionCompressionTask.getFormat(), lRevisionCompressionTask.getSha1(), lRevisionCompressionTask.getRawText(), lRevisionCompressionTask.getCompressedText(), lRevisionCompressionTask.getCompression(), lRevisionCompressionTask.getBytes());
            } else {
                fireRevisionCompressed(lRevisionCompressionTask.getPage(), lRevisionCompressionTask.getRevisionID(), lRevisionCompressionTask.getParentID(), lRevisionCompressionTask.getTimestamp(), lRevisionCompressionTask.getIp(), lRevisionCompressionTask.getComment(), lRevisionCompressionTask.isMinor(), lRevisionCompressionTask.getModel(), lRevisionCompressionTask.getFormat(), lRevisionCompressionTask.getSha1(), lRevisionCompressionTask.getRawText(), lRevisionCompressionTask.getCompressedText(), lRevisionCompressionTask.getCompression(), lRevisionCompressionTask.getBytes());
            }
        }
        writeCompletedRevisionsCounter++;
        if (writeCompletedRevisionsCounter % 100 == 0) {
            logger.info("Compression%: " + ((compressedSum * 100) / (double) uncompressedSum) + ", "+pool.size()+" threads");
        }
    }

    @Override
    public void close() throws InterruptedException, WikiDragonException {
        synchronized (pool) {
            while (pool.size() > 0) {
                try {
                    pool.wait();
                } catch (InterruptedException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        writeCompletedRevisions();
    }

    public void finishGZipCompressionRunnable(GZipCompressionRunnable pGZipCompressionRunnable) throws WikiDragonException {
        synchronized (completedRevisionCompressionTasks) {
            uncompressedSum += pGZipCompressionRunnable.getRevisionCompressionTask().getRawText().length();
            compressedSum += pGZipCompressionRunnable.getRevisionCompressionTask().getCompressedText().length;
            if (pGZipCompressionRunnable.getRevisionCompressionTask().getCompression().equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                compressedCount++;
            }
            else {
                uncompressedCount++;
            }
            completedRevisionCompressionTasks.add(pGZipCompressionRunnable.getRevisionCompressionTask());
        }
        synchronized (pool) {
            pool.remove(pGZipCompressionRunnable);
            pool.notifyAll();
        }
    }
}
