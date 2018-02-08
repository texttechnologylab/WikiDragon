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

import gnu.trove.map.hash.TLongObjectHashMap;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.StringUtil;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.RelTypes.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoPage extends NeoWikiObject implements Page {

    protected static final String ATTR_PAGE_ID = "ATTR_PAGE_ID";
    protected static final String ATTR_PAGE_NAMESPACEID = "ATTR_PAGE_NAMESPACEID";
    protected static final String ATTR_PAGE_TITLE = "ATTR_PAGE_TITLE";
    protected static final String ATTR_PAGE_REVISIONIDS = "ATTR_PAGE_REVISIONIDS"; // not indexed
    protected static final String ATTR_PAGE_REVISIONTIMESTAMPS = "ATTR_PAGE_REVISIONTIMESTAMPS"; // not indexed

    protected static final String ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY = "ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY"; // not indexed

    protected String cachedQualifiedTitle = null;

    protected NeoPage(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    public String getTitle() {
        return (String)getProperty(ATTR_PAGE_TITLE);
    }

    @Override
    public String getQualifiedTitle() throws WikiDragonException {
        if (cachedQualifiedTitle == null) {
            try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
                cachedQualifiedTitle = (String) getProperty(ATTR_PAGE_TITLE);
                String lNamespace = getMediaWiki().getNamespace((int) getProperty(ATTR_PAGE_NAMESPACEID)).getName();
                if (lNamespace.length() > 0) {
                    cachedQualifiedTitle = lNamespace+":"+cachedQualifiedTitle;
                }
                tx.success();
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
        long[] lRevisions = (long[])node.getProperty(ATTR_PAGE_REVISIONIDS, null);
        if (lRevisions == null) lRevisions = updateRevisionListCache();
        return new NeoWikiObjectIterator<Revision>(wikiDragonDatabase, lRevisions);
    }

    @Override
    public PageTier createPageTier(ZonedDateTime pTimestamp) throws WikiDragonException {
        return NeoPageTier.create(this, pTimestamp);
    }

    @Override
    public Revision getRevisionAt(ZonedDateTime pTimestamp) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Revision lResult = null;
            long[] lRevisions = (long[])node.getProperty(ATTR_PAGE_REVISIONIDS, null);
            if (lRevisions == null) lRevisions = updateRevisionListCache();
            if (lRevisions.length > 0) {
                String[] lRevisionTimestampStrings = (String[]) node.getProperty(ATTR_PAGE_REVISIONTIMESTAMPS, null);
                ZonedDateTime[] lRevisionTimestamps = new ZonedDateTime[lRevisionTimestampStrings.length];
                for (int i = 0; i < lRevisionTimestampStrings.length; i++) {
                    lRevisionTimestamps[i] = StringUtil.string2ZonedDateTime(lRevisionTimestampStrings[i]);
                }
                // Is it the latest one?
                if (lRevisionTimestamps[lRevisionTimestamps.length-1].compareTo(pTimestamp) <= 0) {
                    lResult = (NeoRevision)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lRevisionTimestamps.length-1]);
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
                                lResult = (NeoRevision) ((NeoWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lWanderer]);
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
            tx.success();
            return lResult;
        }
    }

    @Override
    public List<Revision> getRevisionsList() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            List<Revision> lResult = new ArrayList<>();
            WikiObjectIterator<Revision> i = getRevisionsIterator();
            while (i.hasNext()) {
                lResult.add(i.next());
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Revision getLatestRevision() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Revision lResult = null;
            long[] lRevisions = (long[])node.getProperty(ATTR_PAGE_REVISIONIDS, null);
            if (lRevisions == null) lRevisions = updateRevisionListCache();
            if (lRevisions.length > 0) {
                lResult = (Revision)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[lRevisions.length-1]);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Revision getFirstRevision() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Revision lResult = null;
            long[] lRevisions = (long[])node.getProperty(ATTR_PAGE_REVISIONIDS, null);
            if (lRevisions == null) lRevisions = updateRevisionListCache();
            if (lRevisions.length > 0) {
                lResult = (Revision)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lRevisions[0]);
            }
            tx.success();
            return lResult;
        }
    }

    public int getRevisionCount() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            long[] lRevisions = (long[]) getProperty(ATTR_PAGE_REVISIONIDS, null);
            if (lRevisions == null) {
                lRevisions = updateRevisionListCache();
            }
            tx.success();
            return lRevisions.length;
        }
    }

    protected long[] updateRevisionListCache() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            long[] lResult = null;
            IndexHits<Node> i = wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoRevision.ATTR_REVISION_PAGENODEID, node.getId());
            TLongObjectHashMap<String> lTimestampMap = new TLongObjectHashMap<>();
            List<Long> lList = new ArrayList<>();
            while (i.hasNext()) {
                Node lNode = i.next();
                lList.add(lNode.getId());
                ZonedDateTime lUTCTimestamp = StringUtil.string2ZonedDateTime((String)lNode.getProperty(NeoRevision.ATTR_REVISION_TIMESTAMP_UTC));
                lTimestampMap.put(lNode.getId(), StringUtil.zonedDateTime2String(lUTCTimestamp));
            }
            i.close();
            lList.sort((s1,s2)->lTimestampMap.get(s1).compareTo(lTimestampMap.get(s2)));
            String[] lTimestampArray = new String[lList.size()];
            lResult = new long[lList.size()];
            for (int k=0; k<lList.size(); k++) {
                lResult[k] = lList.get(k);
                lTimestampArray[k] = lTimestampMap.get(lList.get(k));
            }
            setProperty(ATTR_PAGE_REVISIONIDS, lResult);
            setProperty(ATTR_PAGE_REVISIONTIMESTAMPS, lTimestampArray);
            tx.success();
            return lResult;
        }
    }

    protected void setRevisionListCacheInvalid() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            removeProperty(ATTR_PAGE_REVISIONIDS);
            removeProperty(ATTR_PAGE_REVISIONTIMESTAMPS);
            tx.success();
        }
    }

    @Override
    public PageTier getPageTierAt(ZonedDateTime pTimestamp) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            PageTier lResult = null;
            BooleanQuery lBooleanQuery = new BooleanQuery.Builder().add(new BooleanClause(new TermQuery(new Term(NeoPageTier.ATTR_PAGETIER_PAGENODEID, Long.toString(node.getId()))), BooleanClause.Occur.MUST))
                    .add(new BooleanClause(new TermQuery(new Term(NeoPageTier.ATTR_PAGETIER_TIMESTAMP_UTC, StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC"))))), BooleanClause.Occur.MUST)).build();
            Node lNode = ((NeoWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery).getSingle();
            if (lNode != null) {
                lResult = (PageTier)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public List<PageTier> getPageTierList() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            List<PageTier> lResult = new ArrayList<>();
            IndexHits<Node> lIndexHits = ((NeoWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).get(NeoPageTier.ATTR_PAGETIER_PAGENODEID, Long.toString(node.getId()));
            while (lIndexHits.hasNext()) {
                lResult.add((PageTier)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lIndexHits.next()));
            }
            lIndexHits.close();
            lResult.sort((s1,s2)->s1.getTimestamp().compareTo(s2.getTimestamp()));
            tx.success();
            return lResult;
        }
    }

    @Override
    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, Contributor pContributor, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedData, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        return NeoRevision.create(this, pRevisionID, pParentID, pTimestamp, (NeoContributor)pContributor, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedData, pCompression, pBytes);
    }

    @Override
    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedData, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        return NeoRevision.create(this, pRevisionID, pParentID, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pCompressedData, pCompression, pBytes);
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

    private Set<WikiPageLink> getWikiPageLinks(ZonedDateTime pTimestamp, Direction pDirection, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException {
        Set<String> lFilter = new HashSet<>();
        for (MediaWikiConst.LinkType lLinkType:pLinkTypes) {
            lFilter.add(lLinkType.name());
        }
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
            Set<WikiPageLink> lResult = new HashSet<>();
            for (Relationship r : node.getRelationships()) {
                if (pDirection.equals(Direction.BOTH) || (pDirection.equals(Direction.OUTGOING) && (r.getStartNode().equals(node))) || (pDirection.equals(Direction.INCOMING) && (r.getEndNode().equals(node)))) {
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
                            String[] lTimestamps = (String[]) r.getProperty(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY);
                            boolean lTimestampHit = false;
                            for (String lString : lTimestamps) {
                                if (lString.equals(lTimestampUTCString)) {
                                    lTimestampHit = true;
                                    break;
                                }
                            }
                            if (lTimestampHit) {
                                if (pDirection.equals(Direction.OUTGOING)) {
                                    Page lTarget = (Page) ((NeoWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(r.getEndNode());
                                    lResult.add(new WikiPageLink(lLinkType, this, lTarget, pTimestamp.equals(WikiDragonConst.NULLDATETIME) ? WikiPageLink.WikiPageLinkSource.SQLDump : WikiPageLink.WikiPageLinkSource.HtmlParsedDB, pTimestamp));
                                } else if (pDirection.equals(Direction.INCOMING)) {
                                    Page lSource = (Page) ((NeoWikiObjectFactory) wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(r.getStartNode());
                                    lResult.add(new WikiPageLink(lLinkType, lSource, this, pTimestamp.equals(WikiDragonConst.NULLDATETIME) ? WikiPageLink.WikiPageLinkSource.SQLDump : WikiPageLink.WikiPageLinkSource.HtmlParsedDB, pTimestamp));
                                }
                            }
                        }
                    }
                }
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Map<String, List<WikiDataEntity>> getAspectWikiDataEntityMap() throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Map<String, List<WikiDataEntity>> lResult = new HashMap<>();
            for (Relationship r : node.getRelationships(Direction.OUTGOING, WikiDragonConst.RelTypes.WIKIDATAENTITYLINK)) {
                WikiDataEntity lWikiDataEntity = (WikiDataEntity) wikiDragonDatabase.wikiObjectFactory.getWikiObject(r.getEndNode());
                String lAspect = (String) r.getProperty(NeoWikiDataEntity.ATTR_WIKIDATAENTITY_ASPECT, null);
                if (lAspect != null) {
                    if (!lResult.containsKey(lAspect)) {
                        lResult.put(lAspect, new ArrayList<>());
                    }
                    lResult.get(lAspect).add(lWikiDataEntity);
                }
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public void createAspectWikiDataEntityRelation(WikiDataEntity pWikiDataEntity, String pAspect) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Relationship r = node.createRelationshipTo(((NeoWikiDataEntity)pWikiDataEntity).node, WikiDragonConst.RelTypes.WIKIDATAENTITYLINK);
            r.setProperty(NeoWikiDataEntity.ATTR_WIKIDATAENTITY_ASPECT, pAspect);
            tx.success();
        }
    }

    /**
     * There is at most one relationship of a given type between two pages. Each relationship has a string array
     * containing the timestamps of its validity. WikiDragonConst.NULLDATETIME marks the Timestamp of the SQL Dump.
     */
    @Override
    public void addWikiPageLinksOut(ZonedDateTime pTimestamp, Map<MediaWikiConst.LinkType, Set<Page>> pTypePagesMap) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
            // Fetch Old Status
            Map<WikiDragonConst.RelTypes, Map<Page, Relationship>> lTypePagesMapOld = new HashMap<>();
            for (Relationship r : node.getRelationships(Direction.OUTGOING)) {
                WikiDragonConst.RelTypes lReltype = WikiDragonConst.RelTypes.valueOf(r.getType().name());
                Map<Page, Relationship> lMap = lTypePagesMapOld.get(lReltype);
                if (lMap == null) {
                    lMap = new HashMap<>();
                    lTypePagesMapOld.put(lReltype, lMap);
                }
                lMap.put((Page) wikiDragonDatabase.wikiObjectFactory.getWikiObject(r.getEndNode()), r);
            }

            // Build new Status
            Map<WikiDragonConst.RelTypes, Set<Page>> lTypePagesMapNew = new HashMap<>();
            for (Map.Entry<MediaWikiConst.LinkType, Set<Page>> lEntry : pTypePagesMap.entrySet()) {
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
            for (Map.Entry<WikiDragonConst.RelTypes, Set<Page>> lNewEntry : lTypePagesMapNew.entrySet()) {
                if (!lTypePagesMapOld.containsKey(lNewEntry.getKey())) {
                    for (Page lPage : lNewEntry.getValue()) {
                        Relationship r = node.createRelationshipTo(((NeoPage) lPage).node, lNewEntry.getKey());
                        r.setProperty(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, new String[]{lTimestampUTCString});
                    }
                } else {
                    for (Page lPage : lNewEntry.getValue()) {
                        Relationship r = lTypePagesMapOld.get(lNewEntry.getKey()).get(lPage);
                        if (r == null) {
                            r = node.createRelationshipTo(((NeoPage) lPage).node, lNewEntry.getKey());
                            r.setProperty(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, new String[]{lTimestampUTCString});
                        } else {
                            String[] lTimestamps = (String[]) r.getProperty(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY);
                            TreeSet<String> lTimestampSet = new TreeSet<>();
                            if (lTimestamps != null) {
                                for (String lTimestamp : lTimestamps) {
                                    lTimestampSet.add(lTimestamp);
                                }
                            }
                            lTimestampSet.add(lTimestampUTCString);
                            String[] lNewTimestamps = new String[lTimestampSet.size()];
                            int i = 0;
                            for (String lString : lTimestampSet) {
                                lNewTimestamps[i++] = lString;
                            }
                            r.setProperty(ATTR_PAGE_WIKIPAGELINK_TIMESTAMPARRAY, lNewTimestamps);
                        }
                    }
                }
            }
            tx.success();
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

    protected static NeoPage create(NeoMediaWiki pNeoMediaWiki, long pId, NeoNamespace pNeoNamespace, String pTitle) throws WikiDragonException {
        NeoWikiDragonDatabase lNeoWikiDragonDatabase = pNeoMediaWiki.wikiDragonDatabase;
        try (Transaction tx = lNeoWikiDragonDatabase.database.beginTx()) {
            NeoPage lResult = null;
            // Check if it already exists
            if (pNeoMediaWiki.getPage(pId) != null) throw new WikiDragonException("Page with pageId '"+pId+"' already exists");
            pTitle = pNeoNamespace.getNormalizedPageTitle(pTitle);
            Node lNode = lNeoWikiDragonDatabase.database.createNode();
            lResult = (NeoPage)lNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.PAGE);
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, pNeoMediaWiki.node.getId());
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.PAGE.name());
            lResult.setProperty(ATTR_PAGE_ID, pId);
            lResult.setProperty(ATTR_PAGE_NAMESPACEID, pNeoNamespace.getId());
            lResult.setProperty(ATTR_PAGE_TITLE, pTitle);
            tx.success();
            return lResult;
        }
    }
}
