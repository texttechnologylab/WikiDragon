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

package org.hucompute.wikidragon.core.model.neo;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.PageTier;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.model.WikiPageLink;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.jsoup.Jsoup;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

/**
 * @author Rüdiger Gleim
 */
public class NeoPageTier extends NeoWikiObject implements PageTier {

    protected static final String ATTR_PAGETIER_PAGENODEID = "ATTR_PAGETIER_PAGENODEID";
    protected static final String ATTR_PAGETIER_TIMESTAMP_UTC = "ATTR_PAGETIER_TIMESTAMP_UTC";
    protected static final String ATTR_PAGETIER_TIMESTAMP_ZONEID = "ATTR_PAGETIER_TIMESTAMP_ZONEID";
    protected static final String ATTR_PAGETIER_ATTRIBUTE_COMPRESSED = "ATTR_PAGETIER_ATTRIBUTE_COMPRESSED_";
    protected static final String ATTR_PAGETIER_ATTRIBUTE_COMPRESSION = "ATTR_PAGETIER_ATTRIBUTE_COMPRESSION_";

    protected NeoPageTier(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    public Page getPage() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Page lResult = null;
            long lPageNodeID = (long) getProperty(ATTR_PAGETIER_PAGENODEID, NULLNODEID);
            if (lPageNodeID != NULLNODEID) {
                lResult = (Page) ((NeoWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lPageNodeID);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public ZonedDateTime getTimestamp() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            ZonedDateTime lResult = null;
            String lTimestampString = (String) getProperty(ATTR_PAGETIER_TIMESTAMP_UTC, null);
            if (lTimestampString != null) {
                String lTimeZoneID = (String) getProperty(ATTR_PAGETIER_TIMESTAMP_ZONEID, null);
                if (lTimeZoneID != null) {
                    lResult = StringUtil.string2ZonedDateTime(lTimestampString);
                    lResult = lResult.withZoneSameInstant(ZoneId.of(lTimeZoneID));
                }
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public String getHtml() throws WikiDragonException {
        return getTierAttribute(TierAttribute.HTML);
    }

    @Override
    public String getPlainText() throws WikiDragonException {
        return Jsoup.parse(getHtml()).text();
    }

    @Override
    public void setTierAttribute(TierAttribute pTierAttribute, String pValue) throws WikiDragonException {
        setTierAttribute(pTierAttribute, pValue, WikiDragonConst.Compression.DIFFBZIP2);
    }

    @Override
    public void setTierAttribute(TierAttribute pTierAttribute, String pValue, WikiDragonConst.Compression pDefaultCompression) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            if (pDefaultCompression.equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                Page lPage = getPage();
                List<PageTier> lTierList = lPage.getPageTierList();
                // Get chronological current value (if any)
                String lPrevValue = null;
                for (PageTier lPageTier : lTierList) {
                    if (lPageTier.compareTo(this) < 0) {
                        String lCompressionString = (String) ((NeoPageTier) lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), null);
                        if (lCompressionString != null) {
                            WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                            byte[] lCompressedData = (byte[]) ((NeoPageTier) lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), null);
                            String lDecompressed = null;
                            switch (lCompression) {
                                case NONE:
                                case LZMA2:
                                case GZIP:
                                case BZIP2: {
                                    try {
                                        lDecompressed = IOUtil.uncompress(lCompressedData, lCompression);
                                    } catch (IOException e) {
                                        throw new WikiDragonException(e.getMessage(), e);
                                    }
                                    break;
                                }
                                case DIFFBZIP2: {
                                    String lPatch = null;
                                    try {
                                        lPatch = IOUtil.uncompress(lCompressedData, WikiDragonConst.Compression.BZIP2);
                                    } catch (IOException e) {
                                        throw new WikiDragonException(e.getMessage(), e);
                                    }
                                    DiffMatchPatch lDiff = new DiffMatchPatch();
                                    lDecompressed = (String) lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)lDiff.patchFromText(lPatch), lPrevValue)[0];
                                }
                            }
                            lPrevValue = lDecompressed;
                        }
                    } else {
                        break;
                    }
                }
                // Set the new current value accordingly
                if (lPrevValue == null) {
                    try {
                        node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), IOUtil.compress(pValue, WikiDragonConst.Compression.BZIP2));
                        node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.BZIP2.name());
                    } catch (IOException e) {
                        throw new WikiDragonException(e.getMessage(), e);
                    }
                } else {
                    DiffMatchPatch lDiff = new DiffMatchPatch();
                    LinkedList<DiffMatchPatch.Diff> lDiffs = lDiff.diffMain(lPrevValue, pValue);
                    lDiff.diffCleanupEfficiency(lDiffs);
                    LinkedList<DiffMatchPatch.Patch> lPatches = lDiff.patchMake(lPrevValue, lDiffs);
                    try {
                        byte[] lData = IOUtil.compress(lDiff.patchToText(lPatches), WikiDragonConst.Compression.BZIP2);
                        node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), lData);
                        node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.DIFFBZIP2.name());
                    } catch (IOException e) {
                        throw new WikiDragonException(e.getMessage(), e);
                    }
                }
                // Check and reset values of subsequent tiers
                String lNewValueWanderer = pValue;
                for (PageTier lPageTier : lTierList) {
                    if (lPageTier.compareTo(this) > 0) {
                        String lCompressionString = (String) ((NeoPageTier) lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), null);
                        if (lCompressionString != null) {
                            WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                            byte[] lCompressedData = (byte[]) ((NeoPageTier) lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), null);
                            String lDecompressed = null;
                            switch (lCompression) {
                                case NONE:
                                case LZMA2:
                                case GZIP:
                                case BZIP2: {
                                    try {
                                        lDecompressed = IOUtil.uncompress(lCompressedData, lCompression);
                                    } catch (IOException e) {
                                        throw new WikiDragonException(e.getMessage(), e);
                                    }
                                    break;
                                }
                                case DIFFBZIP2: {
                                    String lPatch = null;
                                    try {
                                        lPatch = IOUtil.uncompress(lCompressedData, WikiDragonConst.Compression.BZIP2);
                                    } catch (IOException e) {
                                        throw new WikiDragonException(e.getMessage(), e);
                                    }
                                    DiffMatchPatch lDiff = new DiffMatchPatch();
                                    lDecompressed = (String) lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)lDiff.patchFromText(lPatch), lPrevValue)[0];
                                }
                            }
                            //
                            DiffMatchPatch lDiff = new DiffMatchPatch();
                            LinkedList<DiffMatchPatch.Diff> lDiffs = lDiff.diffMain(lNewValueWanderer, lDecompressed);
                            lDiff.diffCleanupEfficiency(lDiffs);
                            LinkedList<DiffMatchPatch.Patch> lPatches = lDiff.patchMake(lPrevValue, lDiffs);
                            try {
                                byte[] lData = IOUtil.compress(lDiff.patchToText(lPatches), WikiDragonConst.Compression.BZIP2);
                                ((NeoPageTier) lPageTier).setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), lData);
                                ((NeoPageTier) lPageTier).setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.DIFFBZIP2.name());
                            } catch (IOException e) {
                                throw new WikiDragonException(e.getMessage(), e);
                            }
                            lNewValueWanderer = lDecompressed;
                            lPrevValue = lDecompressed;
                        }
                    }
                }
            }
            else {
                try {
                    node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), IOUtil.compress(pValue, pDefaultCompression));
                    node.setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), pDefaultCompression.name());
                }
                catch (IOException e) {
                    throw new WikiDragonException(e.getMessage(), e);
                }
            }
            tx.success();
        }
    }

    @Override
    public String getTierAttribute(TierAttribute pTierAttribute) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            String lResult = null;
            Page lPage = getPage();
            List<PageTier> lTierList = lPage.getPageTierList();
            // Get chronological current value (if any)
            for (PageTier lPageTier:lTierList) {
                if (lPageTier.compareTo(this) <= 0) {
                    String lCompressionString = (String)((NeoPageTier)lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION+pTierAttribute.name(), null);
                    if (lCompressionString != null) {
                        WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                        byte[] lCompressedData = (byte[])((NeoPageTier)lPageTier).node.getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED+pTierAttribute.name(), null);
                        String lDecompressed = null;
                        switch (lCompression) {
                            case NONE:
                            case LZMA2:
                            case GZIP:
                            case BZIP2: {
                                try {
                                    lDecompressed = IOUtil.uncompress(lCompressedData, lCompression);
                                }
                                catch (IOException e) {
                                    throw new WikiDragonException(e.getMessage(), e);
                                }
                                break;
                            }
                            case DIFFBZIP2: {
                                String lPatch = null;
                                try {
                                    lPatch = IOUtil.uncompress(lCompressedData, WikiDragonConst.Compression.BZIP2);
                                }
                                catch (IOException e) {
                                    throw new WikiDragonException(e.getMessage(), e);
                                }
                                DiffMatchPatch lDiff = new DiffMatchPatch();
                                lDecompressed = (String)lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)lDiff.patchFromText(lPatch), lResult)[0];
                            }
                        }
                        lResult = lDecompressed;
                    }
                }
                else {
                    break;
                }
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public String getTEI() throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            String lResult = wikiDragonDatabase.getIOManager().getTEI(this);
            tx.success();
            return lResult;
        }
    }

    @Override
    public boolean hasTierAttribute(TierAttribute pTierAttribute) {
        return getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION+pTierAttribute.name(), null) != null;
    }

    @Override
    public int compareTo(PageTier o) {
        return getTimestamp().compareTo(o.getTimestamp());
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        return false;
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksIn() throws WikiDragonException {
        return getPage().getWikiPageLinksIn(getTimestamp().withZoneSameInstant(ZoneId.of("UTC")));
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksOut() throws WikiDragonException {
        return getPage().getWikiPageLinksOut(getTimestamp().withZoneSameInstant(ZoneId.of("UTC")));
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            case ATTR_PAGETIER_PAGENODEID: return true;
            case ATTR_PAGETIER_TIMESTAMP_UTC: return true;
            default: return false;
        }
    }

    protected static NeoPageTier create(NeoPage pPage, ZonedDateTime pTimestamp) throws WikiDragonException {
        NeoWikiDragonDatabase lNeoWikiDragonDatabase = pPage.wikiDragonDatabase;
        try (Transaction tx = lNeoWikiDragonDatabase.database.beginTx()) {
            NeoPageTier lResult = null;
            // Check if it already exists
            if (pPage.getPageTierAt(pTimestamp) != null) throw new WikiDragonException("PageTier at '"+ StringUtil.zonedDateTime2String(pTimestamp)+"' already exists");
            long lMediaWikiID = pPage.getMediaWikiId();
            Node lNode = lNeoWikiDragonDatabase.database.createNode();
            lResult = (NeoPageTier)lNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.PAGETIER);
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lMediaWikiID);
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.PAGETIER.name());
            lResult.setProperty(ATTR_PAGETIER_PAGENODEID, pPage.node.getId());
            lResult.setProperty(ATTR_PAGETIER_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))));
            lResult.setProperty(ATTR_PAGETIER_TIMESTAMP_ZONEID, pTimestamp.getZone().getId());
            tx.success();
            return lResult;
        }
    }

}
