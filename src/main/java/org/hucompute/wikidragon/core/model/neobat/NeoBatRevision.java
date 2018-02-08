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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.bouncycastle.util.Iterable;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.HTMLWikiLinkExtraction;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatRevision extends NeoBatWikiObject implements Revision {

    private static Logger logger = LogManager.getLogger(NeoBatRevision.class);

    protected static final String ATTR_REVISION_PAGENODEID = "ATTR_REVISION_PAGENODEID";
    protected static final String ATTR_REVISION_ID = "ATTR_REVISION_ID";
    protected static final String ATTR_REVISION_PARENTID = "ATTR_REVISION_PARENTID";
    protected static final String ATTR_REVISION_TIMESTAMP_UTC = "ATTR_REVISION_TIMESTAMP_UTC";
    protected static final String ATTR_REVISION_TIMESTAMP_ZONEID = "ATTR_REVISION_TIMESTAMP_ZONEID";
    protected static final String ATTR_REVISION_IP = "ATTR_REVISION_IP";
    protected static final String ATTR_REVISION_COMMENT = "ATTR_REVISION_COMMENT";
    protected static final String ATTR_REVISION_MINOR = "ATTR_REVISION_MINOR";
    protected static final String ATTR_REVISION_MODEL = "ATTR_REVISION_MODEL";
    protected static final String ATTR_REVISION_FORMAT = "ATTR_REVISION_FORMAT";
    protected static final String ATTR_REVISION_SHA1 = "ATTR_REVISION_SHA1";
    protected static final String ATTR_REVISION_COMPRESSEDRAWTEXT = "ATTR_REVISION_COMPRESSEDRAWTEXT";
    protected static final String ATTR_REVISION_COMPRESSION = "ATTR_REVISION_COMPRESSION";
    protected static final String ATTR_REVISION_CONTRIBUTORNODEID = "ATTR_REVISION_CONTRIBUTORNODEID";
    protected static final String ATTR_REVISION_COMPRESSEDRAWHTML = "ATTR_REVISION_COMPRESSEDRAWHTML";
    protected static final String ATTR_REVISION_COMPRESSIONHTML = "ATTR_REVISION_COMPRESSIONHTML";
    protected static final String ATTR_REVISION_BYTES = "ATTR_REVISION_BYTES"; // Only applicable for stub-meta-history

    protected NeoBatRevision(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatRevision(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
    }

    @Override
    public long getId() {
        return (long)getProperty(ATTR_REVISION_ID, null);
    }

    @Override
    public ZonedDateTime getTimestamp() {
        ZonedDateTime lResult = null;
        String lTimestampString = (String) getProperty(ATTR_REVISION_TIMESTAMP_UTC, null);
        if (lTimestampString != null) {
            String lTimeZoneID = (String) getProperty(ATTR_REVISION_TIMESTAMP_ZONEID, null);
            if (lTimeZoneID != null) {
                lResult = StringUtil.string2ZonedDateTime(lTimestampString);
                lResult = lResult.withZoneSameInstant(ZoneId.of(lTimeZoneID));
            }
        }
        return lResult;
    }

    @Override
    public Contributor getContributor() {
        long lContributorNodeID = (long)getProperty(ATTR_REVISION_CONTRIBUTORNODEID, NULLNODEID);
        if (lContributorNodeID == NULLNODEID) {
            return null;
        }
        else {
            return (Contributor)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lContributorNodeID);
        }
    }

    @Override
    public String getComment() {
        return (String)getProperty(ATTR_REVISION_COMMENT, null);
    }

    @Override
    public String getIp() {
        return (String)getProperty(ATTR_REVISION_IP, null);
    }

    @Override
    public String getAuthor() {
        String lResult = (String)getProperty(ATTR_REVISION_IP, null);
        if (lResult == null) {
            Contributor lContributor = getContributor();
            if (lContributor != null) {
                lResult = lContributor.getName();
            }
        }
        return lResult;
    }

    @Override
    public MediaWikiConst.Model getModel() {
        MediaWikiConst.Model lResult = null;
        String lModelString = (String) getProperty(ATTR_REVISION_MODEL, null);
        if (lModelString != null) {
            lResult = MediaWikiConst.Model.valueOf(lModelString);
        }
        return lResult;
    }

    @Override
    public MediaWikiConst.Format getFormat() {
        MediaWikiConst.Format lResult = null;
        String lFormatString = (String) getProperty(ATTR_REVISION_FORMAT, null);
        if (lFormatString != null) {
            lResult = MediaWikiConst.Format.valueOf(lFormatString);
        }
        return lResult;
    }

    @Override
    public String getSHA1() {
        return (String)getProperty(ATTR_REVISION_SHA1, null);
    }

    @Override
    public byte[] getCompressedRawText() {
        return (byte[])getProperty(ATTR_REVISION_COMPRESSEDRAWTEXT, null);
    }

    @Override
    public Revision getParentRevision() {
        Revision lResult = null;
        long lRevisionID = (long)getProperty(ATTR_REVISION_PARENTID, NULLNODEID);
        if (lRevisionID != NULLNODEID) {
            Long lNode = wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(ATTR_REVISION_ID, lRevisionID).getSingle();
            lResult = (Revision) ((NeoBatWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public String getRawText() throws WikiDragonException {
        String lResult = null;
        byte[] lBytes = getCompressedRawText();
        WikiDragonConst.Compression lCompression = getRawTextCompression();
        switch (lCompression) {
            case NONE:
            case LZMA2:
            case GZIP:
            case BZIP2: {
                try {
                    lResult = IOUtil.uncompress(lBytes, lCompression);
                }
                catch (IOException e) {
                    throw new WikiDragonException(e.getMessage(), e);
                }
                break;
            }
            case DIFFBZIP2: {
                List<NeoBatRevision> lList = new LinkedList<>();
                NeoBatRevision lWanderer = (NeoBatRevision) getParentRevision();
                lList.add(this);
                while (lWanderer.getRawTextCompression().equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                    lList.add(0, lWanderer);
                    lWanderer = (NeoBatRevision)lWanderer.getParentRevision();
                }
                lList.add(0, lWanderer);
                for (NeoBatRevision lNeoBatRevision:lList) {
                    String lCurrentText = null;
                    WikiDragonConst.Compression lCurrentCompression = lNeoBatRevision.getRawTextCompression();
                    switch (lCurrentCompression) {
                        case NONE:
                        case BZIP2: {
                            try {
                                lCurrentText = IOUtil.uncompress(lNeoBatRevision.getCompressedRawText(), lCurrentCompression);
                            }
                            catch (IOException e) {
                                throw new WikiDragonException(e.getMessage(), e);
                            }
                            break;
                        }
                        case DIFFBZIP2: {
                            try {
                                lCurrentText = IOUtil.uncompress(lNeoBatRevision.getCompressedRawText(), WikiDragonConst.Compression.BZIP2);
                            }
                            catch (IOException e) {
                                throw new WikiDragonException(e.getMessage(), e);
                            }
                            break;
                        }
                    }
                    if (lResult == null) {
                        lResult = lCurrentText;
                    }
                    else {
                        if (lCurrentCompression.equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                            DiffMatchPatch lDiff = new DiffMatchPatch();
                            lResult = (String)lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)lDiff.patchFromText(lCurrentText), lResult)[0];
                        }
                        else {
                            lResult = lCurrentText;
                        }
                    }
                }
            }
        }
        return lResult;
    }

    @Override
    public Set<WikiPageLink> getWikiPageOutLinks() throws WikiDragonException {
        return getWikiPageOutLinks(new MediaWikiConst.LinkType[]{});
    }

    @Override
    public Set<WikiPageLink> getWikiPageOutLinks(MediaWikiConst.LinkType... pAcceptLinkTypes) throws WikiDragonException {
        NeoBatMediaWiki lMediaWiki = ((NeoBatMediaWiki) ((NeoBatWikiObjectFactory) getWikiDragonDatabase().getWikiObjectFactory()).getWikiObject(Long.toString(getMediaWikiId())));
        Set<MediaWikiConst.LinkType> lAccept = new HashSet<>();
        for (MediaWikiConst.LinkType lType:pAcceptLinkTypes) {
            lAccept.add(lType);
        }
        Map<Integer, Map<String, Page>> lCacheMap = new HashMap<>();
        Set<WikiPageLink> lPreResult = HTMLWikiLinkExtraction.extractWikiPageLinks(lMediaWiki, getPage(), getHtml(), getTimestamp(), lCacheMap);
        Set<WikiPageLink> lResult = new HashSet<>();
        if (lAccept.size()> 0) {
            for (WikiPageLink lLink:lPreResult) {
                if (lAccept.contains(lLink.getLinkType())) {
                    lResult.add(lLink);
                }
            }
        }
        else {
            lResult = lPreResult;
        }
        return lResult;
    }

    @Override
    public String getTEI() throws WikiDragonException {
        return wikiDragonDatabase.getIOManager().getTEI(this);
    }

    @Override
    public String getPlainText() throws WikiDragonException {
        return Jsoup.parse(getHtml()).text();
    }

    @Override
    public boolean isMinor() throws WikiDragonException {
        return (boolean)getProperty(ATTR_REVISION_MINOR, false);
    }

    @Override
    public String getHtml() throws WikiDragonException {
        String lResult = null;
        byte[] lBytes = getCompressedRawHtml();
        if (lBytes == null) {
            lResult = ((NeoBatMediaWiki) ((NeoBatWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(getMediaWikiId())).getXOWAParser().parse(getPage(), this);
        }
        else {
            WikiDragonConst.Compression lCompression = getHtmlCompression();
            switch (lCompression) {
                case NONE:
                case LZMA2:
                case GZIP:
                case BZIP2: {
                    try {
                        lResult = IOUtil.uncompress(lBytes, lCompression);
                    } catch (IOException e) {
                        throw new WikiDragonException(e.getMessage(), e);
                    }
                    break;
                }
                case DIFFBZIP2: {
                    List<NeoBatRevision> lList = new LinkedList<>();
                    NeoBatRevision lWanderer = (NeoBatRevision) getParentRevision();
                    lList.add(this);
                    while (lWanderer.getHtmlCompression().equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                        lList.add(0, lWanderer);
                        lWanderer = (NeoBatRevision) lWanderer.getParentRevision();
                    }
                    lList.add(0, lWanderer);
                    for (NeoBatRevision lNeoBatRevision : lList) {
                        String lCurrentHtml = null;
                        WikiDragonConst.Compression lCurrentCompression = lNeoBatRevision.getHtmlCompression();
                        switch (lCurrentCompression) {
                            case NONE:
                            case BZIP2: {
                                try {
                                    lCurrentHtml = IOUtil.uncompress(lNeoBatRevision.getCompressedRawHtml(), lCurrentCompression);
                                } catch (IOException e) {
                                    throw new WikiDragonException(e.getMessage(), e);
                                }
                                break;
                            }
                            case DIFFBZIP2: {
                                try {
                                    lCurrentHtml = IOUtil.uncompress(lNeoBatRevision.getCompressedRawHtml(), WikiDragonConst.Compression.BZIP2);
                                } catch (IOException e) {
                                    throw new WikiDragonException(e.getMessage(), e);
                                }
                                break;
                            }
                        }
                        if (lResult == null) {
                            lResult = lCurrentHtml;
                        } else {
                            if (lCurrentCompression.equals(WikiDragonConst.Compression.DIFFBZIP2)) {
                                DiffMatchPatch lDiff = new DiffMatchPatch();
                                lResult = (String) lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)lDiff.patchFromText(lCurrentHtml), lResult)[0];
                            } else {
                                lResult = lCurrentHtml;
                            }
                        }
                    }
                }
            }
        }
        return lResult;
    }

    @Override
    public Page getPage() {
        Page lResult = null;
        long lPageNodeID = (long) getProperty(ATTR_REVISION_PAGENODEID, NULLNODEID);
        if (lPageNodeID != NULLNODEID) {
            lResult = (Page) ((NeoBatWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lPageNodeID);
        }
        return lResult;
    }

    @Override
    public WikiDragonConst.Compression getRawTextCompression() {
        WikiDragonConst.Compression lResult = null;
        String lCompressionString = (String) getProperty(ATTR_REVISION_COMPRESSION, null);
        if (lCompressionString != null) {
            lResult = WikiDragonConst.Compression.valueOf(lCompressionString);
        }
        return lResult;
    }

    @Override
    public void setCompressedRawHtml(byte[] pData) {
        setProperty(ATTR_REVISION_COMPRESSEDRAWHTML, pData);
    }

    @Override
    public byte[] getCompressedRawHtml() {
        return (byte[])getProperty(ATTR_REVISION_COMPRESSEDRAWHTML, null);
    }

    @Override
    public WikiDragonConst.Compression getHtmlCompression() {
        WikiDragonConst.Compression lResult = null;
        String lCompressionString = (String) getProperty(ATTR_REVISION_COMPRESSIONHTML, null);
        if (lCompressionString != null) {
            lResult = WikiDragonConst.Compression.valueOf(lCompressionString);
        }
        return lResult;
    }

    @Override
    public void setHtmlCompression(WikiDragonConst.Compression pCompression) {
        setProperty(ATTR_REVISION_COMPRESSIONHTML, pCompression.name());
    }

    @Override
    public Iterable<RevisionContentTuple> getRevisionContentTuples(RevisionSequenceContentIterator.RevisionContentType pRevisionContentType) {
        return new Iterable<RevisionContentTuple>() {
            @Override
            public Iterator<RevisionContentTuple> iterator() {
                return getRevisionContentTupleIterator(pRevisionContentType);
            }
        };
    }

    @Override
    public int getBytes() throws WikiDragonException {
        int lResult = (int)getProperty(ATTR_REVISION_BYTES, -1);
        if (lResult == -1) {
            String lRawText = getRawText();
            if (lRawText != null) {
                lResult = lRawText.getBytes(Charset.forName("UTF-8")).length;
            }
        }
        return lResult;
    }

    @Override
    public RevisionSequenceContentIterator getRevisionContentTupleIterator(RevisionSequenceContentIterator.RevisionContentType pRevisionContentType) {
        return new RevisionSequenceContentIterator(this, pRevisionContentType);
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        return false;
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        switch (pProperty) {
            case ATTR_REVISION_PAGENODEID: return true;
            case ATTR_REVISION_ID: return true;
            case ATTR_REVISION_PARENTID: return true;
            case ATTR_REVISION_TIMESTAMP_UTC: return true;
            case ATTR_REVISION_IP: return true;
            default: return false;
        }
    }

    protected static NeoBatRevision create(NeoBatPage pPage, long pRevisionID, long pParentID, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedRawText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        NeoBatWikiDragonDatabase lNeoBatWikiDragonDatabase = pPage.wikiDragonDatabase;
        NeoBatMediaWiki lNeoBatMediaWiki = (NeoBatMediaWiki)pPage.getMediaWiki();
        try {
            pPage.setRevisionListCacheInvalid();
            NeoBatRevision lResult = null;
            // Check if it already exists
            if (lNeoBatMediaWiki.getRevision(pRevisionID) != null) throw new WikiDragonException("Revision with revisionId '"+pRevisionID+"' already exists");
            long lNode = lNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
            lResult = (NeoBatRevision)lNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.REVISION);
            lResult.disableAutosaveOnce();
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.REVISION.name());
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lNeoBatMediaWiki.node);
            lResult.setProperty(ATTR_REVISION_PAGENODEID, pPage.node);
            lResult.setProperty(ATTR_REVISION_ID, pRevisionID);
            if (pParentID != NULLNODEID) lResult.setProperty(ATTR_REVISION_PARENTID, pParentID);
            lResult.setProperty(ATTR_REVISION_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))));
            lResult.setProperty(ATTR_REVISION_TIMESTAMP_ZONEID, pTimestamp.getZone().getId());
            if (pIP != null) lResult.setProperty(ATTR_REVISION_IP, pIP);
            if ((pComment != null) && (pComment.length() > 0)) lResult.setProperty(ATTR_REVISION_COMMENT, pComment);
            lResult.setProperty(ATTR_REVISION_MINOR, pMinor);
            if (pModel == null) pModel = MediaWikiConst.Model.WIKITEXT;
            lResult.setProperty(ATTR_REVISION_MODEL, pModel.name());
            if (pFormat == null) pFormat = MediaWikiConst.Format.TEXT_XWIKI;
            lResult.setProperty(ATTR_REVISION_FORMAT, pFormat.name());
            lResult.setProperty(ATTR_REVISION_SHA1, pSHA1);
            lResult.setProperty(ATTR_REVISION_COMPRESSEDRAWTEXT, pCompressedRawText);
            lResult.setProperty(ATTR_REVISION_COMPRESSION, pCompression.name());
            lResult.setProperty(ATTR_REVISION_BYTES, pBytes);
            lResult.saveProperties();
            return lResult;
        }
        catch (Exception e) {
            logger.error("Page="+pPage.getTitle()+", RevisionID="+pRevisionID);
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

    protected static NeoBatRevision create(NeoBatPage pPage, long pRevisionID, long pParentID, ZonedDateTime pTimestamp, NeoBatContributor pContributor, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedRawText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        NeoBatWikiDragonDatabase lNeoBatWikiDragonDatabase = pPage.wikiDragonDatabase;
        NeoBatMediaWiki lNeoBatMediaWiki = (NeoBatMediaWiki)pPage.getMediaWiki();
        try {
            pPage.setRevisionListCacheInvalid();
            NeoBatRevision lResult = null;
            // Check if it already exists
            if (lNeoBatMediaWiki.getRevision(pRevisionID) != null) throw new WikiDragonException("Revision with revisionId '"+pRevisionID+"' already exists");
            long lNode = lNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
            lResult = (NeoBatRevision)lNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.REVISION);
            lResult.disableAutosaveOnce();
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.REVISION.name());
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lNeoBatMediaWiki.node);
            lResult.setProperty(ATTR_REVISION_PAGENODEID, pPage.node);
            lResult.setProperty(ATTR_REVISION_ID, pRevisionID);
            if (pParentID != NULLNODEID) lResult.setProperty(ATTR_REVISION_PARENTID, pParentID);
            lResult.setProperty(ATTR_REVISION_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))));
            lResult.setProperty(ATTR_REVISION_TIMESTAMP_ZONEID, pTimestamp.getZone().getId());
            if (pContributor.node != NULLNODEID) lResult.setProperty(ATTR_REVISION_CONTRIBUTORNODEID, pContributor.node);
            if ((pComment != null) && (pComment.length() > 0)) lResult.setProperty(ATTR_REVISION_COMMENT, pComment);
            lResult.setProperty(ATTR_REVISION_MINOR, pMinor);
            if (pModel == null) pModel = MediaWikiConst.Model.WIKITEXT;
            lResult.setProperty(ATTR_REVISION_MODEL, pModel.name());
            if (pFormat == null) pFormat = MediaWikiConst.Format.TEXT_XWIKI;
            lResult.setProperty(ATTR_REVISION_FORMAT, pFormat.name());
            lResult.setProperty(ATTR_REVISION_SHA1, pSHA1);
            lResult.setProperty(ATTR_REVISION_COMPRESSEDRAWTEXT, pCompressedRawText);
            lResult.setProperty(ATTR_REVISION_COMPRESSION, pCompression.name());
            lResult.setProperty(ATTR_REVISION_BYTES, pBytes);
            lResult.saveProperties();
            return lResult;
        }
        catch (Exception e) {
            logger.error("Page="+pPage.getTitle()+", RevisionID="+pRevisionID);
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

}
