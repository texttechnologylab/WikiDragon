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

import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.bouncycastle.util.Iterable;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.unsafe.batchinsert.BatchRelationship;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.RelTypes.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatPage extends NeoBatWikiObject implements Page {

    protected static final String ATTR_PAGE_ID = "ATTR_PAGE_ID";
    protected static final String ATTR_PAGE_NAMESPACEID = "ATTR_PAGE_NAMESPACEID";
    protected static final String ATTR_PAGE_TITLE = "ATTR_PAGE_TITLE";
    protected static final String ATTR_PAGE_REVISIONIDS = "ATTR_PAGE_REVISIONIDS"; // not indexed
    protected static final String ATTR_PAGE_REVISIONTIMESTAMPS = "ATTR_PAGE_REVISIONTIMESTAMPS"; // not indexed

    protected static final String ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY = "ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY"; // not indexed

    protected String cachedQualifiedTitle;

    protected NeoBatPage(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatPage(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
    }

    @Override
    public String getTitle() {
        return (String)getProperty(ATTR_PAGE_TITLE);
    }

    @Override
    public String getQualifiedTitle() throws WikiDragonException {
        if (cachedQualifiedTitle == null) {
            cachedQualifiedTitle = (String) getProperty(ATTR_PAGE_TITLE);
            String lNamespace = getMediaWiki().getNamespace((int) getProperty(ATTR_PAGE_NAMESPACEID)).getName();
            if (lNamespace.length() > 0) {
                cachedQualifiedTitle = lNamespace+":"+cachedQualifiedTitle;
            }
        }
        return cachedQualifiedTitle;
    }

    @Override
    public int getNamespaceID() {
        return (int)getProperty(ATTR_PAGE_NAMESPACEID);
    }

    @Override
    public long getId() {
        return (long)getProperty(ATTR_PAGE_ID);
    }

    @Override
    public Iterable<Revision> getRevisions() {
        return new Iterable<Revision>() {
            @Override
            public Iterator<Revision> iterator() {
                return getRevisionsIterator();
            }
        };
    }

    @Override
    public WikiObjectIterator<Revision> getRevisionsIterator() {
        long[] lRevisions = (long[])getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) lRevisions = updateRevisionListCache();
        return new NeoBatWikiObjectIterator<Revision>(wikiDragonDatabase, lRevisions);
    }

    @Override
    public PageTier createPageTier(ZonedDateTime pTimestamp) throws WikiDragonException {
        return NeoBatPageTier.create(this, pTimestamp);
    }

    @Override
    public Revision getRevisionAt(ZonedDateTime pTimestamp) {
        Revision lResult = null;
        long[] lRevisions = (long[])getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) lRevisions = updateRevisionListCache();
        if (lRevisions.length > 0) {
            String[] lRevisionTimestampStrings = (String[])getProperty(ATTR_PAGE_REVISIONTIMESTAMPS, null);
            ZonedDateTime[] lRevisionTimestamps = new ZonedDateTime[lRevisionTimestampStrings.length];
            for (int i = 0; i < lRevisionTimestampStrings.length; i++) {
                lRevisionTimestamps[i] = StringUtil.string2ZonedDateTime(lRevisionTimestampStrings[i]);
            }
            // Is it the latest one?
            if (lRevisionTimestamps[lRevisionTimestamps.length-1].compareTo(pTimestamp) <= 0) {
                lResult = (NeoBatRevision)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lRevisionTimestamps.length-1]);
            }
            // Is it before the first one?
            else if (pTimestamp.compareTo(lRevisionTimestamps[0]) < 0) {
                lResult = null;
            }
            else {
                // Ok- somewhere in between
                int lMin = 0;
                int lMax = lRevisionTimestamps.length-1;
                int lWanderer = (int)Math.floor(lRevisionTimestamps.length/2d);
                do {
                    if (lRevisionTimestamps[lWanderer].compareTo(pTimestamp) <= 0) {
                        if (lRevisionTimestamps[lWanderer+1].compareTo(pTimestamp) > 0) {
                            lResult = (NeoBatRevision) ((NeoBatWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lWanderer]);
                            break;
                        }
                        else {
                            lMin = lWanderer+1;
                        }
                    }
                    else {
                        lMax = lWanderer-1;
                    }
                    lWanderer = lMin + (int)Math.floor((lMax-lMin)/2d);
                } while (true);
            }
        }
        return lResult;
    }

    @Override
    public List<Revision> getRevisionsList() {
        List<Revision> lResult = new ArrayList<>();
        WikiObjectIterator<Revision> i = getRevisionsIterator();
        while (i.hasNext()) {
            lResult.add(i.next());
        }
        return lResult;
    }

    @Override
    public Revision getLatestRevision() {
        Revision lResult = null;
        long[] lRevisions = (long[])getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) lRevisions = updateRevisionListCache();
        if (lRevisions.length > 0) {
            lResult = (Revision)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lRevisions.length-1]);
        }
        return lResult;
    }

    @Override
    public Revision getFirstRevision() {
        Revision lResult = null;
        long[] lRevisions = (long[])getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) lRevisions = updateRevisionListCache();
        if (lRevisions.length > 0) {
            lResult = (Revision)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[0]);
        }
        return lResult;
    }

    public int getRevisionCount() {
        long[] lRevisions = (long[])getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) {
            lRevisions = updateRevisionListCache();
        }
        return lRevisions.length;
    }

    protected long[] updateRevisionListCache() {
        long[] lResult = null;
        IndexHits<Long> i = wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoBatRevision.ATTR_REVISION_PAGENODEID, node);
        TLongObjectHashMap<String> lTimestampMap = new TLongObjectHashMap<>();
        List<Long> lList = new ArrayList<>();
        while (i.hasNext()) {
            Long lNode = i.next();
            lList.add(lNode);
            NeoBatRevision lRevision = new NeoBatRevision(wikiDragonDatabase, lNode);
            ZonedDateTime lUTCTimestamp = StringUtil.string2ZonedDateTime((String)lRevision.getProperty(NeoBatRevision.ATTR_REVISION_TIMESTAMP_UTC));
            lTimestampMap.put(lNode, StringUtil.zonedDateTime2String(lUTCTimestamp));
        }
        i.close();
        lList.sort((s1,s2)->lTimestampMap.get(s1).compareTo(lTimestampMap.get(s2)));
        String[] lTimestampArray = new String[lList.size()];
        lResult = new long[lList.size()];
        for (int k=0; k<lList.size(); k++) {
            lResult[k] = lList.get(k);
            lTimestampArray[k] = lTimestampMap.get(lList.get(k));
        }
        disableAutosaveOnce();
        setProperty(ATTR_PAGE_REVISIONIDS, lResult);
        setProperty(ATTR_PAGE_REVISIONTIMESTAMPS, lTimestampArray);
        saveProperties();
        return lResult;
    }

    protected void setRevisionListCacheInvalid() {
        removeProperty(ATTR_PAGE_REVISIONIDS);
        removeProperty(ATTR_PAGE_REVISIONTIMESTAMPS);
    }

    @Override
    public PageTier getPageTierAt(ZonedDateTime pTimestamp) {
        PageTier lResult = null;
        BooleanQuery lBooleanQuery = new BooleanQuery.Builder().add(new BooleanClause(new TermQuery(new Term(NeoBatPageTier.ATTR_PAGETIER_PAGENODEID, Long.toString(node))), BooleanClause.Occur.MUST))
                .add(new BooleanClause(new TermQuery(new Term(NeoBatPageTier.ATTR_PAGETIER_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))))), BooleanClause.Occur.MUST)).build();
        Long lNode = ((NeoBatWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery).getSingle();
        if (lNode != null) {
            lResult = (PageTier)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public List<PageTier> getPageTierList() {
        List<PageTier> lResult = new ArrayList<>();
        IndexHits<Long> lIndexHits = ((NeoBatWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).get(NeoBatPageTier.ATTR_PAGETIER_PAGENODEID, Long.toString(node));
        while (lIndexHits.hasNext()) {
            lResult.add((PageTier)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lIndexHits.next()));
        }
        lIndexHits.close();
        lResult.sort((s1,s2)->s1.getTimestamp().compareTo(s2.getTimestamp()));
        return lResult;
    }

    @Override
    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, Contributor pContributor, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedData, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        return NeoBatRevision.create(this, pRevisionID, pParentID, pTimestamp, (NeoBatContributor)pContributor, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedData, pCompression, pBytes);
    }

    @Override
    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedData, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        return NeoBatRevision.create(this, pRevisionID, pParentID, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedData, pCompression, pBytes);
    }

    @Override
    public MediaWiki getMediaWiki() {
        return (MediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(getMediaWikiId());
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksIn() throws WikiDragonException {
        return getWikiPageLinksIn(WikiDragonConst.NULLDATETIME);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksIn(ZonedDateTime pTimestamp) throws WikiDragonException {
        return getWikiPageLinks(pTimestamp, Direction.INCOMING);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksOut() throws WikiDragonException {
        return getWikiPageLinksOut(WikiDragonConst.NULLDATETIME);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksOut(ZonedDateTime pTimestamp) throws WikiDragonException {
        return getWikiPageLinks(pTimestamp, Direction.OUTGOING);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksIn(MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        return getWikiPageLinksIn(WikiDragonConst.NULLDATETIME, pLinkTypes);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksIn(ZonedDateTime pTimestamp, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        return getWikiPageLinks(pTimestamp, Direction.INCOMING, pLinkTypes);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksOut(MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        return getWikiPageLinksOut(WikiDragonConst.NULLDATETIME, pLinkTypes);
    }

    @Override
    public Set<WikiPageLink> getWikiPageLinksOut(ZonedDateTime pTimestamp, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        return getWikiPageLinks(pTimestamp, Direction.OUTGOING, pLinkTypes);
    }

    @Override
    public Map<String, List<WikiDataEntity>> getAspectWikiDataEntityMap() throws WikiDragonException {
        Map<String, List<WikiDataEntity>> lResult = new HashMap<>();
        for (BatchRelationship r:wikiDragonDatabase.database.getRelationships(node)) {
            if ((r.getStartNode() == node) && r.getType().name().equals(WikiDragonConst.RelTypes.WIKIDATAENTITYLINK.name())) {
                WikiDataEntity lWikiDataEntity = (WikiDataEntity)wikiDragonDatabase.wikiObjectFactory.getWikiObject(r.getEndNode());
                Map<String, Object> lProperties = wikiDragonDatabase.database.getRelationshipProperties(r.getId());
                String lAspect = (String)lProperties.get(NeoBatWikiDataEntity.ATTR_WIKIDATAENTITY_ASPECT);
                if (lAspect != null) {
                    if (!lResult.containsKey(lAspect)) {
                        lResult.put(lAspect, new ArrayList<>());
                    }
                    lResult.get(lAspect).add(lWikiDataEntity);
                }
            }
        }
        return lResult;
    }

    @Override
    public void createAspectWikiDataEntityRelation(WikiDataEntity pWikiDataEntity, String pAspect) throws WikiDragonException {
        Map<String, Object> lMap = new HashMap<>();
        lMap.put(NeoBatWikiDataEntity.ATTR_WIKIDATAENTITY_ASPECT, pAspect);
        wikiDragonDatabase.database.createRelationship(node, ((NeoBatWikiDataEntity)pWikiDataEntity).node, WikiDragonConst.RelTypes.WIKIDATAENTITYLINK, lMap);
    }

    private Set<WikiPageLink> getWikiPageLinks(ZonedDateTime pTimestamp, Direction pDirection, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        Set<String> lFilter = new HashSet<>();
        for (MediaWikiConst.LinkType lLinkType:pLinkTypes) {
            lFilter.add(lLinkType.name());
        }
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        Set<WikiPageLink> lResult = new HashSet<>();
        for (BatchRelationship r:wikiDragonDatabase.database.getRelationships(node)) {
            if (pDirection.equals(Direction.BOTH) || (pDirection.equals(Direction.OUTGOING) && (r.getStartNode() == node)) || (pDirection.equals(Direction.INCOMING) && (r.getEndNode() == node))) {
                WikiDragonConst.RelTypes lReltype = WikiDragonConst.RelTypes.valueOf(r.getType().name());
                MediaWikiConst.LinkType lLinkType = null;
                switch (lReltype) {
                    case WIKIPAGELINK_ARTICLE: {
                        lLinkType = MediaWikiConst.LinkType.ARTICLE;
                        break;
                    }
                    case WIKIPAGELINK_CATEGORIZATION: {
                        lLinkType = MediaWikiConst.LinkType.CATEGORIZATION;
                        break;
                    }
                    case WIKIPAGELINK_REDIRECT: {
                        lLinkType = MediaWikiConst.LinkType.REDIRECT;
                        break;
                    }
                }
                if (lLinkType != null) {
                    if ((lFilter.size() == 0) || lFilter.contains(lLinkType.name())) {
                        String[] lTimestamps = (String[]) wikiDragonDatabase.database.getRelationshipProperties(r.getId()).get(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY);
                        boolean lTimestampHit = false;
                        for (String lString : lTimestamps) {
                            if (lString.equals(lTimestampUTCString)) {
                                lTimestampHit = true;
                                break;
                            }
                        }
                        if (lTimestampHit) {
                            if (pDirection.equals(Direction.OUTGOING)) {
                                Page lTarget = (NeoBatPage) wikiDragonDatabase.getWikiObjectFactory().getWikiObject(Long.toString(r.getEndNode()));
                                lResult.add(new WikiPageLink(lLinkType, this, lTarget, pTimestamp.equals(WikiDragonConst.NULLDATETIME) ? WikiPageLink.WikiPageLinkSource.SQLDump : WikiPageLink.WikiPageLinkSource.HtmlParsedDB, pTimestamp));
                            } else if (pDirection.equals(Direction.INCOMING)) {
                                Page lSource = (NeoBatPage) wikiDragonDatabase.getWikiObjectFactory().getWikiObject(Long.toString(r.getStartNode()));
                                lResult.add(new WikiPageLink(lLinkType, lSource, this, pTimestamp.equals(WikiDragonConst.NULLDATETIME) ? WikiPageLink.WikiPageLinkSource.SQLDump : WikiPageLink.WikiPageLinkSource.HtmlParsedDB, pTimestamp));
                            }
                        }
                    }
                }
            }
        }
        return lResult;
    }

    /**
     * There is at most one relationship of a given type between two pages. Each relationship has a string array
     * containing the timestamps of its validity. WikiDragonConst.NULLDATETIME marks the Timestamp of the SQL Dump.
     */
    public void addWikiPageLinksOut(ZonedDateTime pTimestamp, Map<MediaWikiConst.LinkType, Set<Page>> pTypePagesMap) throws WikiDragonException {
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        // Fetch Old Status
        Map<WikiDragonConst.RelTypes, Map<Page, BatchRelationship>> lTypePagesMapOld = new HashMap<>();
        for (BatchRelationship r:wikiDragonDatabase.database.getRelationships(node)) {
            if (r.getStartNode() == node) {
                WikiDragonConst.RelTypes lReltype = WikiDragonConst.RelTypes.valueOf(r.getType().name());
                Map<Page, BatchRelationship> lMap = lTypePagesMapOld.get(lReltype);
                if (lMap == null) {
                    lMap = new HashMap<>();
                    lTypePagesMapOld.put(lReltype, lMap);
                }
                lMap.put((Page) wikiDragonDatabase.wikiObjectFactory.getWikiObject(r.getEndNode()), r);
            }
        }

        // Build new Status
        Map<WikiDragonConst.RelTypes, Set<Page>> lTypePagesMapNew = new HashMap<>();
        for (Map.Entry<MediaWikiConst.LinkType, Set<Page>> lEntry:pTypePagesMap.entrySet()) {
            switch (lEntry.getKey()) {
                case ARTICLE: {
                    lTypePagesMapNew.put(WIKIPAGELINK_ARTICLE, lEntry.getValue());
                    break;
                }
                case CATEGORIZATION: {
                    lTypePagesMapNew.put(WIKIPAGELINK_CATEGORIZATION, lEntry.getValue());
                    break;
                }
                case REDIRECT: {
                    lTypePagesMapNew.put(WIKIPAGELINK_REDIRECT, lEntry.getValue());
                    break;
                }
            }
        }

        // Add/Update Relationships
        for (Map.Entry<WikiDragonConst.RelTypes, Set<Page>> lNewEntry:lTypePagesMapNew.entrySet()) {
            if (!lTypePagesMapOld.containsKey(lNewEntry.getKey())) {
                for (Page lPage:lNewEntry.getValue()) {
                    Map<String, Object > lParamMap = new HashMap<>();
                    lParamMap.put(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, new String[]{lTimestampUTCString});
                    wikiDragonDatabase.database.createRelationship(node, ((NeoBatPage)lPage).node, lNewEntry.getKey(), lParamMap);
                }
            }
            else {
                for (Page lPage:lNewEntry.getValue()) {
                    BatchRelationship r = lTypePagesMapOld.get(lNewEntry.getKey()).get(lPage);
                    if (r == null) {
                        Map<String, Object > lParamMap = new HashMap<>();
                        lParamMap.put(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, new String[]{lTimestampUTCString});
                        wikiDragonDatabase.database.createRelationship(node, ((NeoBatPage)lPage).node, lNewEntry.getKey(), lParamMap);
                    }
                    else {
                        Map<String, Object> lParamMap = wikiDragonDatabase.database.getRelationshipProperties(r.getId());
                        String[] lTimestamps = (String[])lParamMap.get(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY);
                        TreeSet<String> lTimestampSet = new TreeSet<>();
                        if (lTimestamps != null) {
                            for (String lTimestamp:lTimestamps) {
                                lTimestampSet.add(lTimestamp);
                            }
                        }
                        lTimestampSet.add(lTimestampUTCString);
                        String[] lNewTimestamps = new String[lTimestampSet.size()];
                        int i=0;
                        for (String lString:lTimestampSet) {
                            lNewTimestamps[i++] = lString;
                        }
                        lParamMap.put(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, lNewTimestamps);
                        wikiDragonDatabase.database.setRelationshipProperties(r.getId(), lParamMap);
                    }
                }
            }
        }
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        return false;
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            case ATTR_PAGE_ID: return true;
            case ATTR_PAGE_NAMESPACEID: return true;
            case ATTR_PAGE_TITLE: return true;
            default: return false;
        }
    }

    protected static NeoBatPage create(NeoBatMediaWiki pNeoBatMediaWiki, long pId, NeoBatNamespace pNeoBatNamespace, String pTitle) throws WikiDragonException {
        NeoBatWikiDragonDatabase lNeoBatWikiDragonDatabase = pNeoBatMediaWiki.wikiDragonDatabase;
        NeoBatPage lResult = null;
        // Check if it already exists
        if (pNeoBatMediaWiki.getPage(pId) != null) throw new WikiDragonException("Page with pageId '"+pId+"' already exists");
        pTitle = pNeoBatNamespace.getNormalizedPageTitle(pTitle);
        long lNode = lNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
        lResult = (NeoBatPage)lNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.PAGE);
        lResult.disableAutosaveOnce();
        lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.PAGE.name());
        lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, pNeoBatMediaWiki.node);
        lResult.setProperty(ATTR_PAGE_ID, pId);
        lResult.setProperty(ATTR_PAGE_NAMESPACEID, pNeoBatNamespace.getId());
        lResult.setProperty(ATTR_PAGE_TITLE, pTitle);
        lResult.saveProperties();
        return lResult;
    }
}
