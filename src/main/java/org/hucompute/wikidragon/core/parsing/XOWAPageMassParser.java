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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.Revision;
import org.hucompute.wikidragon.core.parsing.filter.XOWAPageMassParserAllFilter;
import org.hucompute.wikidragon.core.parsing.filter.XOWAPageMassParserFilter;

import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class XOWAPageMassParser {

    private static Logger logger = LogManager.getLogger(XOWAPageMassParser.class);

    protected int keyFrameRate = 50;

    protected Set<XOWAPageMassParserRunnable> available;
    protected Set<XOWAPageMassParserRunnable> used;
    protected int maxThreads = 0;
    protected Map<Revision, String> revisionHtmlMap;
    protected long uncompressedBytes = 0;
    protected long compressedBytes = 0;
    protected WikiDragonException exception;
    protected long errors;
    protected XOWAPageMassParserFilter xowaPageMassParserFilter;
    protected MediaWiki mediaWiki;
    protected Collection<Page> pages;

    public XOWAPageMassParser(MediaWiki pMediaWiki, Collection<Page> pPages) {
        this(pMediaWiki, pPages, Math.max(java.lang.Runtime.getRuntime().availableProcessors() / 2, 1), new XOWAPageMassParserAllFilter());
    }

    public XOWAPageMassParser(MediaWiki pMediaWiki, Collection<Page> pPages, int pMaxThreads, XOWAPageMassParserFilter pXOWAPageMassParserFilter) {
        mediaWiki = pMediaWiki;
        xowaPageMassParserFilter = pXOWAPageMassParserFilter;
        maxThreads = pMaxThreads;
        pages = pPages;
    }

    public void parse() throws WikiDragonException {
        logger.info("Initializing Parsing. Pages total: "+pages.size());
        available = new HashSet<>();
        used = new HashSet<>();
        revisionHtmlMap = new HashMap<>();
        errors = 0;
        for (int i = 0; i < maxThreads; i++) {
            available.add(new XOWAPageMassParserRunnable(this, mediaWiki));
        }
        int lPageCounter = 0;
        long lRevisionCounter = 0;
        long lMaxRevisions = 0;
        Map<Page, List<Revision>> lRevisionMap = new HashMap<>();
        logger.info("Fetching Revisions: "+pages.size()+" pages total");
        for (Page lPage:pages) {
            lPageCounter++;
            List<Revision> lRevisionList = lPage.getRevisionsList();
            {
                Iterator<Revision> i = lRevisionList.iterator();
                while (i.hasNext()) {
                    Revision lRevision = i.next();
                    if (!xowaPageMassParserFilter.acceptParsing(lPage, lRevisionList, lRevision)
                            || (lRevision.getCompressedRawHtml() != null)) {
                        i.remove();
                    }
                }
            }
            lRevisionMap.put(lPage, lRevisionList);
            lMaxRevisions += lRevisionList.size();
            logger.info("Counting Revisions: "+lPageCounter+" pages, "+lMaxRevisions+" revisions");
        }
        logger.info("Fetching Revisions: Done");
        lPageCounter = 0;
        long lStart = System.currentTimeMillis();
        for (Map.Entry<Page, List<Revision>> lEntry:lRevisionMap.entrySet()) {
            lPageCounter++;
            Revision lPrevRevision = null;
            for (int i=0; i<lEntry.getValue().size(); i++) {
                lRevisionCounter++;
                Revision lRevision = lEntry.getValue().get(i);
                synchronized (used) {
                    while (used.size() >= maxThreads) {
                        try {
                            used.wait();
                        } catch (InterruptedException e) {
                            logger.error(e);
                        }
                    }
                    XOWAPageMassParserRunnable lXOWAPageMassParserRunnable = available.iterator().next();
                    available.remove(lXOWAPageMassParserRunnable);
                    used.add(lXOWAPageMassParserRunnable);
                    lXOWAPageMassParserRunnable.setData(lEntry.getKey(), lPrevRevision, lRevision, i == lEntry.getValue().size()-1, i%keyFrameRate == 0);
                    new Thread(lXOWAPageMassParserRunnable).start();
                }
                lPrevRevision = lRevision;
                double lEstimated = (((System.currentTimeMillis()-lStart)/(double)lRevisionCounter)*(lMaxRevisions-lRevisionCounter))/(1000*60*60d);
                logger.info("Parsing and Compressing "+lPageCounter+"/"+pages.size()+", "+lRevisionCounter+" revs, "+((compressedBytes*100d)/uncompressedBytes)+"% compression, "+used.size()+"/"+maxThreads+" threads, "+errors+" errors, "+lEstimated+" hours");
            }
        }
        // Wait for remaining Threads to complete
        logger.info("Parsing: Waiting for pending threads to complete");
        synchronized (used) {
            while (used.size() > 0) {
                try {
                    used.wait();
                } catch (InterruptedException e) {
                    logger.error(e);
                }
            }
        }
        logger.info("Parsing: Done");
    }

    public Map<Revision, String> getRevisionHtmlMap() {
        return revisionHtmlMap;
    }

    public void finishXOWATask(XOWAPageMassParserRunnable pXOWAPageMassParserRunnable) {
        try {
            if (pXOWAPageMassParserRunnable.getException() == null) {
                if (pXOWAPageMassParserRunnable.getCompressedResult() != null) {
                    pXOWAPageMassParserRunnable.getRevision().setCompressedRawHtml(pXOWAPageMassParserRunnable.getCompressedResult());
                    pXOWAPageMassParserRunnable.getRevision().setHtmlCompression(pXOWAPageMassParserRunnable.getCompression());
                    synchronized (used) {
                        compressedBytes += pXOWAPageMassParserRunnable.getCompressedResult().length;
                        uncompressedBytes += pXOWAPageMassParserRunnable.getResult().getBytes(Charset.forName("UTF-8")).length;
                    }
                }
            }
            else {
                logger.error(pXOWAPageMassParserRunnable.getException().getMessage(), pXOWAPageMassParserRunnable.getException());
                errors++;
            }
        }
        finally {
            synchronized (used) {
                used.remove(pXOWAPageMassParserRunnable);
                available.add(pXOWAPageMassParserRunnable);
                used.notifyAll();
            }
        }
    }

}
