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
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.parsing.filter.XOWATierMassParserFilter;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public class XOWATierMassParser {

    private static Logger logger = LogManager.getLogger(XOWATierMassParser.class);

    protected Set<XOWATierMassParserRunnable> available;
    protected Set<XOWATierMassParserRunnable> used;
    protected int maxThreads = 0;
    protected ZonedDateTime timestamp;
    protected WikiDragonConst.Compression defaultCompression = WikiDragonConst.Compression.BZIP2;
    protected MediaWiki mediaWiki;
    protected Map<String, String> titleTextCacheMap;
    protected Map<PageTier, String> pendingResultWriteMap;
    protected XOWATierMassParserFilter xowaTierMassParserFilter;

    public XOWATierMassParser(MediaWiki pMediaWiki, ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter) throws WikiDragonException {
        this(pMediaWiki, pTimestamp, pXOWATierMassParserFilter, Math.min(java.lang.Runtime.getRuntime().availableProcessors()/2, 1));
    }

    public XOWATierMassParser(MediaWiki pMediaWiki, ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter, int pMaxThreads) throws WikiDragonException {
        mediaWiki = pMediaWiki;
        timestamp = pTimestamp;
        maxThreads = pMaxThreads;
        xowaTierMassParserFilter = pXOWATierMassParserFilter;
    }

    public void parse() throws WikiDragonException {
        titleTextCacheMap = new HashMap<>();
        available = new HashSet<>();
        used = new HashSet<>();
        for (int i=0; i<maxThreads; i++) {
            available.add(new XOWATierMassParserRunnable(this, mediaWiki));
        }
        pendingResultWriteMap = new HashMap<>();
        int lCacheClearCounter = 0;
        logger.info("Collecting pages to parse");
        Set<Page> lPages = new HashSet<>();
        try (WikiTransaction tx = mediaWiki.getWikiDragonDatabase().beginTx()) {
            WikiObjectIterator<Page> i = mediaWiki.getPageIterator();
            while (i.hasNext()) {
                Page lPage = i.next();
                if (xowaTierMassParserFilter.acceptParsing(lPage)) {
                    lPages.add(lPage);
                }
            }
            i.close();
            tx.success();
        }
        logger.info("Parsing revisions");
        long lRevisionCounter = 0;
        long lParsedPagesCounter = 0;
        for (Page lPage:lPages) {
            try (WikiTransaction tx = mediaWiki.getWikiDragonDatabase().beginTx()) {
                Revision lRevision = lPage.getRevisionAt(timestamp);
                if (lRevision != null) {
                    PageTier lPageTier = lPage.getPageTierAt(timestamp);
                    if (lPageTier == null) {
                        lPageTier = lPage.createPageTier(timestamp);
                    }
                    if (!lPageTier.hasTierAttribute(PageTier.TierAttribute.HTML)) {
                        synchronized (this) {
                            while (available.size() == 0) {
                                try {
                                    this.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            // Clear Cache periodically to avoid flooding of cache
                            if (lCacheClearCounter++ > 1000) {
                                synchronized (titleTextCacheMap) {
                                    titleTextCacheMap.clear();
                                }
                            }
                            //
                            XOWATierMassParserRunnable lXOWATierMassParserRunnable = available.iterator().next();
                            available.remove(lXOWATierMassParserRunnable);
                            lXOWATierMassParserRunnable.setParameters(lPage, lRevision, lPageTier, timestamp);
                            used.add(lXOWATierMassParserRunnable);
                            available.remove(lXOWATierMassParserRunnable);
                            new Thread(lXOWATierMassParserRunnable).start();
                        }
                        lParsedPagesCounter++;
                        writePendingResults();
                    }
                }
                lRevisionCounter++;
                if (lRevisionCounter % 100 == 0) {
                    logger.info("Processing pages: " + lRevisionCounter + "/" + lPages.size() + ", parsed: " + lParsedPagesCounter);
                }
                tx.success();
            }
        }
        synchronized (this) {
            while (used.size() > 0) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        try (WikiTransaction tx = mediaWiki.getWikiDragonDatabase().beginTx()) {
            writePendingResults();
        }
        logger.info("Processing complete: " + lRevisionCounter + "/" + lPages.size()+", parsed: "+lParsedPagesCounter);
    }

    public WikiDragonConst.Compression getDefaultCompression() {
        return defaultCompression;
    }

    public XOWATierMassParserFilter getXowaTierMassParserFilter() {
        return xowaTierMassParserFilter;
    }

    public void setXowaTierMassParserFilter(XOWATierMassParserFilter xowaTierMassParserFilter) {
        this.xowaTierMassParserFilter = xowaTierMassParserFilter;
    }

    public void setDefaultCompression(WikiDragonConst.Compression defaultCompression) {
        this.defaultCompression = defaultCompression;
    }

    protected void writePendingResults() throws WikiDragonException {
        Map<PageTier, String> lMap = null;
        synchronized (pendingResultWriteMap) {
            lMap = new HashMap<>(pendingResultWriteMap);
            pendingResultWriteMap.clear();
        }
        for (Map.Entry<PageTier, String> lEntry:lMap.entrySet()) {
            lEntry.getKey().setTierAttribute(PageTier.TierAttribute.HTML, lEntry.getValue(), defaultCompression);
        }
    }

    public Map<String, String> getTitleTextCacheMap() {
        return titleTextCacheMap;
    }

    public void finishTask(XOWATierMassParserRunnable pXOWATierMassParserRunnable) {
        try {
            if (pXOWATierMassParserRunnable.getResult() != null) {
                synchronized (pendingResultWriteMap) {
                    pendingResultWriteMap.put(pXOWATierMassParserRunnable.getPageTier(), pXOWATierMassParserRunnable.getResult());
                }
            }
        }
        finally {
            synchronized (this) {
                used.remove(pXOWATierMassParserRunnable);
                available.add(pXOWATierMassParserRunnable);
                this.notifyAll();
            }
        }
    }

}
