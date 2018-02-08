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

package org.hucompute.wikidragon.core.model.neobat;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.PageTier;
import org.hucompute.wikidragon.core.model.WikiDragonConst;
import org.hucompute.wikidragon.core.model.WikiPageLink;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatPageTier extends NeoBatWikiObject implements PageTier {

    protected static final String ATTR_PAGETIER_PAGENODEID = "ATTR_PAGETIER_PAGENODEID";
    protected static final String ATTR_PAGETIER_TIMESTAMP_UTC = "ATTR_PAGETIER_TIMESTAMP_UTC";
    protected static final String ATTR_PAGETIER_TIMESTAMP_ZONEID = "ATTR_PAGETIER_TIMESTAMP_ZONEID";
    protected static final String ATTR_PAGETIER_ATTRIBUTE_COMPRESSED = "ATTR_PAGETIER_ATTRIBUTE_COMPRESSED_";
    protected static final String ATTR_PAGETIER_ATTRIBUTE_COMPRESSION = "ATTR_PAGETIER_ATTRIBUTE_COMPRESSION_";

    protected NeoBatPageTier(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatPageTier(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
    }

    @Override
    public Page getPage() {
        Page lResult = null;
        long lPageNodeID = (long) getProperty(ATTR_PAGETIER_PAGENODEID, NULLNODEID);
        if (lPageNodeID != NULLNODEID) {
            lResult = (Page) ((NeoBatWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lPageNodeID);
        }
        return lResult;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        ZonedDateTime lResult = null;
        String lTimestampString = (String) getProperty(ATTR_PAGETIER_TIMESTAMP_UTC, null);
        if (lTimestampString != null) {
            String lTimeZoneID = (String) getProperty(ATTR_PAGETIER_TIMESTAMP_ZONEID, null);
            if (lTimeZoneID != null) {
                lResult = StringUtil.string2ZonedDateTime(lTimestampString);
                lResult = lResult.withZoneSameInstant(ZoneId.of(lTimeZoneID));
            }
        }
        return lResult;
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
        if (pDefaultCompression.equals(WikiDragonConst.Compression.DIFFBZIP2)) {
            Page lPage = getPage();
            List<PageTier> lTierList = lPage.getPageTierList();
            // Get chronological current value (if any)
            String lPrevValue = null;
            for (PageTier lPageTier : lTierList) {
                if (lPageTier.compareTo(this) < 0) {
                    String lCompressionString = (String) ((NeoBatPageTier) lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), null);
                    if (lCompressionString != null) {
                        WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                        byte[] lCompressedData = (byte[]) ((NeoBatPageTier) lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), null);
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
                    disableAutosaveOnce();
                    setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), IOUtil.compress(pValue, WikiDragonConst.Compression.BZIP2));
                    setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.BZIP2.name());
                    saveProperties();
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
                    disableAutosaveOnce();
                    setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), lData);
                    setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.DIFFBZIP2.name());
                    saveProperties();
                } catch (IOException e) {
                    throw new WikiDragonException(e.getMessage(), e);
                }
            }
            // Check and reset values of subsequent tiers
            String lNewValueWanderer = pValue;
            for (PageTier lPageTier : lTierList) {
                if (lPageTier.compareTo(this) > 0) {
                    String lCompressionString = (String) ((NeoBatPageTier) lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), null);
                    if (lCompressionString != null) {
                        WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                        byte[] lCompressedData = (byte[]) ((NeoBatPageTier) lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), null);
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
                            ((NeoBatPageTier) lPageTier).disableAutosaveOnce();
                            ((NeoBatPageTier) lPageTier).setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), lData);
                            ((NeoBatPageTier) lPageTier).setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), WikiDragonConst.Compression.DIFFBZIP2.name());
                            ((NeoBatPageTier) lPageTier).saveProperties();
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
                disableAutosaveOnce();
                setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED + pTierAttribute.name(), IOUtil.compress(pValue, pDefaultCompression));
                setProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION + pTierAttribute.name(), pDefaultCompression.name());
                saveProperties();
            } catch (IOException e) {
                throw new WikiDragonException(e.getMessage(), e);
            }
        }
    }

    @Override
    public String getTierAttribute(TierAttribute pTierAttribute) throws WikiDragonException {
        String lResult = null;
        Page lPage = getPage();
        List<PageTier> lTierList = lPage.getPageTierList();
        // Get chronological current value (if any)
        for (PageTier lPageTier:lTierList) {
            if (lPageTier.compareTo(this) <= 0) {
                String lCompressionString = (String)((NeoBatPageTier)lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION+pTierAttribute.name(), null);
                if (lCompressionString != null) {
                    WikiDragonConst.Compression lCompression = WikiDragonConst.Compression.valueOf(lCompressionString);
                    byte[] lCompressedData = (byte[])((NeoBatPageTier)lPageTier).getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSED+pTierAttribute.name(), null);
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
        return lResult;
    }

    @Override
    public boolean hasTierAttribute(TierAttribute pTierAttribute) {
        return getProperty(ATTR_PAGETIER_ATTRIBUTE_COMPRESSION+pTierAttribute.name(), null) != null;
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
    public String getTEI() throws WikiDragonException {
        return wikiDragonDatabase.getIOManager().getTEI(this);
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
    protected boolean isIndexedMediaWiki(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            case ATTR_PAGETIER_PAGENODEID: return true;
            case ATTR_PAGETIER_TIMESTAMP_UTC: return true;
            default: return false;
        }
    }

    protected static NeoBatPageTier create(NeoBatPage pPage, ZonedDateTime pTimestamp) throws WikiDragonException {
        NeoBatWikiDragonDatabase lNeoBatWikiDragonDatabase = pPage.wikiDragonDatabase;
        NeoBatPageTier lResult = null;
        // Check if it already exists
        if (pPage.getPageTierAt(pTimestamp) != null)
            throw new WikiDragonException("PageTier at '" + StringUtil.zonedDateTime2String(pTimestamp) + "' already exists");
        long lMediaWikiID = pPage.getMediaWikiId();
        long lNode = lNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
        lResult = (NeoBatPageTier) lNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.PAGETIER);
        lResult.disableAutosaveOnce();
        lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.PAGETIER.name());
        lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lMediaWikiID);
        lResult.setProperty(ATTR_PAGETIER_PAGENODEID, pPage.node);
        lResult.setProperty(ATTR_PAGETIER_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))));
        lResult.setProperty(ATTR_PAGETIER_TIMESTAMP_ZONEID, pTimestamp.getZone().getId());
        lResult.saveProperties();
        return lResult;
    }

}
