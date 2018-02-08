package org.hucompute.wikidragon.core.revcompression.lzma2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressionTask;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressor;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.WikiDragonConst;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

public class LZMA2RevisionCompressor extends RevisionCompressor {

    private static Logger logger = LogManager.getLogger(LZMA2RevisionCompressor.class);

    protected int maxThreads;
    protected MediaWiki mediaWiki;

    protected Set<LZMA2CompressionRunnable> pool;
    protected List<RevisionCompressionTask> completedRevisionCompressionTasks;

    protected long uncompressedSum = 0;
    protected long compressedSum = 0;
    protected long compressedCount = 0;
    protected long uncompressedCount = 0;

    protected long writeCompletedRevisionsCounter;

    public LZMA2RevisionCompressor() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors()/2));
    }

    public LZMA2RevisionCompressor(int pMaxThreads) {
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
                LZMA2CompressionRunnable lLZMA2CompressionRunnable = new LZMA2CompressionRunnable(this, lRevisionCompressionTask);
                pool.add(lLZMA2CompressionRunnable);
                new Thread(lLZMA2CompressionRunnable).start();
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

    public void finishXZCompressionRunnable(LZMA2CompressionRunnable pLZMA2CompressionRunnable) throws WikiDragonException {
        synchronized (completedRevisionCompressionTasks) {
            uncompressedSum += pLZMA2CompressionRunnable.getRevisionCompressionTask().getRawText().length();
            compressedSum += pLZMA2CompressionRunnable.getRevisionCompressionTask().getCompressedText().length;
            if (pLZMA2CompressionRunnable.getRevisionCompressionTask().getCompression().equals(WikiDragonConst.Compression.LZMA2)) {
                compressedCount++;
            }
            else {
                uncompressedCount++;
            }
            completedRevisionCompressionTasks.add(pLZMA2CompressionRunnable.getRevisionCompressionTask());
        }
        synchronized (pool) {
            pool.remove(pLZMA2CompressionRunnable);
            pool.notifyAll();
        }
    }
}
