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

package org.hucompute.wikidragon.core.revcompression.diff;

import gnu.trove.list.linked.TLongLinkedList;
import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
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
public class DiffRevisionCompressor extends RevisionCompressor {

    private static Logger logger = LogManager.getLogger(DiffRevisionCompressor.class);

    public static final int DEFAULT_KEYFRAME_INTERVAL = 50;
    public static final boolean DEFAULT_LATEST_REVISION_UNCOMPRESSED = true;

    protected int maxThreads;
    protected int rawTextCacheSize;
    protected MediaWiki mediaWiki;

    protected Set<DiffBZip2CompressionRunnable> pool;
    protected List<RevisionDiffCompressionTask> completedRevisionCompressionTasks;

    protected RevisionDiffCompressionTask nextSubmissionCandidate;
    protected int currentPageKeyFrameCounter;
    protected int keyFrameInterval = DEFAULT_KEYFRAME_INTERVAL;
    protected boolean forceLatestRevisionUncompressed = DEFAULT_LATEST_REVISION_UNCOMPRESSED;

    protected TLongObjectHashMap<String> revisionIDRawTextCache;
    protected TLongLinkedList revisionIDRawTextCacheSequence;

    protected long uncompressedSum = 0;
    protected long compressedSum = 0;
    protected long compressedCount = 0;
    protected long uncompressedCount = 0;

    protected long writeCompletedRevisionsCounter;

    public DiffRevisionCompressor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()/2), Math.max(1, Runtime.getRuntime().availableProcessors()/2)*2);
    }

    public DiffRevisionCompressor(int pMaxThreads, int pRawTextCacheSize) {
        mediaWiki = null;
        maxThreads = pMaxThreads;
        rawTextCacheSize = pRawTextCacheSize;
        pool = new HashSet<>();
        completedRevisionCompressionTasks = new ArrayList<>();
        revisionIDRawTextCache = new TLongObjectHashMap<>();
        revisionIDRawTextCacheSequence = new TLongLinkedList();
        nextSubmissionCandidate = null;
        writeCompletedRevisionsCounter = 0;
    }

    public void finishDiffBZip2CompressionRunnable(DiffBZip2CompressionRunnable pDiffBZip2CompressionRunnable) throws WikiDragonException {
        synchronized (completedRevisionCompressionTasks) {
            uncompressedSum += pDiffBZip2CompressionRunnable.getRevisionCompressionTask().getRawText().length();
            compressedSum += pDiffBZip2CompressionRunnable.getRevisionCompressionTask().getCompressedText().length;
            if (pDiffBZip2CompressionRunnable.getRevisionCompressionTask().getCompression().equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                compressedCount++;
            }
            else {
                uncompressedCount++;
            }
            completedRevisionCompressionTasks.add(pDiffBZip2CompressionRunnable.getRevisionCompressionTask());
        }
        synchronized (pool) {
            pool.remove(pDiffBZip2CompressionRunnable);
            pool.notifyAll();
        }
    }

    public int getKeyFrameInterval() {
        return keyFrameInterval;
    }

    public void setKeyFrameInterval(int keyFrameInterval) {
        this.keyFrameInterval = keyFrameInterval;
    }

    public boolean isForceLatestRevisionUncompressed() {
        return forceLatestRevisionUncompressed;
    }

    public void setForceLatestRevisionUncompressed(boolean forceLatestRevisionUncompressed) {
        this.forceLatestRevisionUncompressed = forceLatestRevisionUncompressed;
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
            // Save new entry to cache
            pushCache(pRevisionID, pRawText);
            // Prepare joint RevisionDiffCompressionTask
            RevisionDiffCompressionTask lRevisionCompressionTask;
            if (pUserID != NULLNODEID) {
                lRevisionCompressionTask = new RevisionDiffCompressionTask(pPage, pRevisionID, pParentId, pTimestamp, pUserName, pUserID, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, false, pBytes);
            }
            else {
                lRevisionCompressionTask = new RevisionDiffCompressionTask(pPage, pRevisionID, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, false, pBytes);
            }
            // Do we have a pending nextSubmissionCandidate?
            if (nextSubmissionCandidate != null) {
                // Yes- is the pending Candidate from the same page as the new revision?
                if (nextSubmissionCandidate.getPage().equals(pPage)) {
                    // Yes- check if a new keyframe applies for the new page
                    currentPageKeyFrameCounter++;
                    if ((keyFrameInterval > 0) && (currentPageKeyFrameCounter == keyFrameInterval)) {
                        currentPageKeyFrameCounter = 0;
                        lRevisionCompressionTask.setForceKeyFrame(true);
                    }

                } else {
                    // No- a new page starts
                    // Force the last revision of the last page to be uncompressed (if applicable)
                    nextSubmissionCandidate.setForceKeyFrame(forceLatestRevisionUncompressed);
                    // Prepare new page. Also handle key frames
                    currentPageKeyFrameCounter = 0;
                    currentPageKeyFrameCounter++;
                    boolean lForceKeyFrame = false;
                    if ((keyFrameInterval > 0) && (currentPageKeyFrameCounter == keyFrameInterval)) {
                        currentPageKeyFrameCounter = 0;
                        lRevisionCompressionTask.setForceKeyFrame(true);
                    }
                }
                submitNextSubmissionCandidate();
            } else {
                // No old candidate at all
                currentPageKeyFrameCounter = 0;
                currentPageKeyFrameCounter++;
                if ((keyFrameInterval > 0) && (currentPageKeyFrameCounter == keyFrameInterval)) {
                    currentPageKeyFrameCounter = 0;
                    lRevisionCompressionTask.setForceKeyFrame(true);
                }
            }
            nextSubmissionCandidate = lRevisionCompressionTask;
        }
    }

    protected void submitNextSubmissionCandidate() throws WikiDragonException {
        if (nextSubmissionCandidate != null) {
            synchronized (pool) {
                while (pool.size() >= maxThreads) {
                    try {
                        pool.wait();
                    } catch (InterruptedException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
                DiffBZip2CompressionRunnable lDiffBZip2CompressionRunnable = new DiffBZip2CompressionRunnable(this, nextSubmissionCandidate);
                pool.add(lDiffBZip2CompressionRunnable);
                new Thread(lDiffBZip2CompressionRunnable).start();
            }
            writeCompletedRevisions();
            nextSubmissionCandidate = null;
        }
    }

    @Override
    public void close() throws WikiDragonException {
        synchronized (pool) {
            submitNextSubmissionCandidate();
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

    public String getRawText(long pRevisionID) throws WikiDragonException {
        String lResult = null;
        if (pRevisionID == NULLNODEID) return lResult;
        synchronized (revisionIDRawTextCache) {
            lResult = revisionIDRawTextCache.get(pRevisionID);
            revisionIDRawTextCache.remove(pRevisionID);
            revisionIDRawTextCacheSequence.remove(pRevisionID);
        }
        if (lResult == null) {
            Revision lRevision = mediaWiki.getRevision(pRevisionID);
            if (lRevision != null) {
                lResult = lRevision.getRawText();
            }
        }
        return lResult;
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
            logger.info("Compression%: " + ((compressedSum * 100) / (double) uncompressedSum) + ", Diff%-Ratio: " + ((((compressedCount) * 100) / (double) (compressedCount+uncompressedCount)))+", "+pool.size()+" threads");
        }
    }

    private void pushCache(long pRevisionID, String pRawText) {
        synchronized (revisionIDRawTextCache) {
            revisionIDRawTextCache.put(pRevisionID, pRawText);
            revisionIDRawTextCacheSequence.add(pRevisionID);
            while (revisionIDRawTextCacheSequence.size() > rawTextCacheSize) {
                long lRevisionID = revisionIDRawTextCacheSequence.removeAt(0);
                revisionIDRawTextCache.remove(lRevisionID);
            }
        }
    }
}
