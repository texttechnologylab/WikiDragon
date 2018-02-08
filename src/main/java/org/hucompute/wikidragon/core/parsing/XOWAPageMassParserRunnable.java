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

package org.hucompute.wikidragon.core.parsing;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.Revision;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.util.IOUtil;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author Rüdiger Gleim
 */
public class XOWAPageMassParserRunnable implements Runnable {

    private static Logger logger = LogManager.getLogger(XOWAPageMassParserRunnable.class);

    protected XOWAPageMassParser xowaPageMassParser;
    protected MediaWiki mediaWiki;
    protected XOWAParser xowaParser;
    protected Page page;
    protected Revision revision;
    protected Revision prevRevision;
    protected WikiDragonException exception;
    protected String text;
    protected String pageTitle;
    protected String result;
    protected boolean lastRevisionOfPage;
    protected byte[] compressedResult;
    protected WikiDragonConst.Compression compression;
    protected boolean forceKeyFrame;

    public XOWAPageMassParserRunnable(XOWAPageMassParser pXOWAPageMassParser, MediaWiki pMediaWiki) throws WikiDragonException {
        xowaPageMassParser = pXOWAPageMassParser;
        mediaWiki = pMediaWiki;
        xowaParser = new XOWAParser(mediaWiki);
    }

    public WikiDragonException getException() {
        return exception;
    }

    public byte[] getCompressedResult() {
        return compressedResult;
    }

    public WikiDragonConst.Compression getCompression() {
        return compression;
    }

    public String getResult() {
        return result;
    }

    public Page getPage() {
        return page;
    }

    public Revision getRevision() {
        return revision;
    }

    public boolean isLastRevisionOfPage() {
        return lastRevisionOfPage;
    }

    public void setData(Page pPage, Revision pPrevRevision, Revision pRevision, boolean pLastRevisionOfPage, boolean pForceKeyFrame) {
        page = pPage;
        prevRevision = pPrevRevision;
        revision = pRevision;
        lastRevisionOfPage = pLastRevisionOfPage;
        forceKeyFrame = pForceKeyFrame;
    }

    @Override
    public void run() {
        int lAttempts = 16;
        for (int attempt=0; attempt < lAttempts; attempt++) {
            exception = null;
            try {
                if (attempt > 0) {
                    xowaParser = new XOWAParser(mediaWiki);
                }
                // Parse
                text = revision.getRawText();
                assert text != null;
                pageTitle = page.getTitle();
                assert pageTitle != null;
                xowaParser.clearCache(); // Clear Cache to avoid flooding of cache
                try {
                    result = xowaParser.parse(pageTitle, text, revision.getTimestamp());
                } catch (Exception e) {
                    logger.error("Failed");
                    throw e;
                }
                Map<Revision, String> lRevisionHtmlMap = xowaPageMassParser.getRevisionHtmlMap();
                synchronized (lRevisionHtmlMap) {
                    if (!lastRevisionOfPage) {
                        lRevisionHtmlMap.put(revision, result);
                        synchronized (revision) {
                            revision.notifyAll();
                        }
                    }
                }
                // Compress
                if (prevRevision == null) {
                    compressedResult = IOUtil.compress(result, WikiDragonConst.Compression.BZIP2);
                    compression = WikiDragonConst.Compression.BZIP2;
                } else {
                    String lPrevRevisionHtml = null;
                    while (lPrevRevisionHtml == null) {
                        synchronized (lRevisionHtmlMap) {
                            lPrevRevisionHtml = lRevisionHtmlMap.remove(prevRevision);
                        }
                        if (lPrevRevisionHtml == null) {
                            synchronized (prevRevision) {
                                prevRevision.wait(50);
                            }
                        }
                    }
                    if (!forceKeyFrame) {
                        DiffMatchPatch lDiff = new DiffMatchPatch();
                        LinkedList<DiffMatchPatch.Diff> lDiffs = lDiff.diffMain(lPrevRevisionHtml, result);
                        lDiff.diffCleanupEfficiency(lDiffs);
                        LinkedList<DiffMatchPatch.Patch> lPatches = lDiff.patchMake(lPrevRevisionHtml, lDiffs);
                        ByteArrayOutputStream lByteArrayOutputStream = new ByteArrayOutputStream();
                        BZip2CompressorOutputStream lBZip2CompressorOutputStream = new BZip2CompressorOutputStream(lByteArrayOutputStream);
                        byte[] lData = lDiff.patchToText(lPatches).getBytes(Charset.forName("UTF-8"));
                        lBZip2CompressorOutputStream.write(lData);
                        lBZip2CompressorOutputStream.flush();
                        lBZip2CompressorOutputStream.close();
                        compressedResult = lByteArrayOutputStream.toByteArray();
                        compression = WikiDragonConst.Compression.DIFFBZIP2;
                    } else {
                        compressedResult = IOUtil.compress(result, WikiDragonConst.Compression.BZIP2);
                        compression = WikiDragonConst.Compression.BZIP2;
                    }
                }
                break;
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
                if (e instanceof WikiDragonException) {
                    exception = (WikiDragonException) e;
                } else {
                    exception = new WikiDragonException(e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
        if (exception != null) {
            logger.warn("Giving up after "+lAttempts+" attempts on: "+pageTitle+" - "+revision.getId());
            compressedResult = new String("").getBytes(Charset.forName("UTF-8"));
            compression = WikiDragonConst.Compression.NONE;
        }
        xowaPageMassParser.finishXOWATask(this);
    }

}
