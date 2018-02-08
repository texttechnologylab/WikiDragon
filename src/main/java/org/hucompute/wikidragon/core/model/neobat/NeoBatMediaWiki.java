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
import gnu.trove.set.hash.TLongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Iterable;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.parsing.XOWAParser;
import org.hucompute.wikidragon.core.parsing.XOWATierMassParser;
import org.hucompute.wikidragon.core.parsing.filter.XOWATierMassParserFilter;
import org.hucompute.wikidragon.core.util.HTMLWikiLinkExtraction;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.SQLTupelIterator;
import org.hucompute.wikidragon.core.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatMediaWiki extends NeoBatWikiObject implements MediaWiki {

    protected static final String ATTR_MEDIAWIKI_DBNAME = "ATTR_MEDIAWIKI_DBNAME";
    protected static final String ATTR_MEDIAWIKI_SITENAME = "ATTR_MEDIAWIKI_SITENAME";
    protected static final String ATTR_MEDIAWIKI_BASE = "ATTR_MEDIAWIKI_BASE";
    protected static final String ATTR_MEDIAWIKI_GENERATOR = "ATTR_MEDIAWIKI_GENERATOR";
    protected static final String ATTR_MEDIAWIKI_CASE = "ATTR_MEDIAWIKI_CASE";
    protected static final String ATTR_MEDIAWIKI_APIURL = "ATTR_MEDIAWIKI_APIURL";
    protected static final String ATTR_MEDIAWIKI_NAMESPACES = "ATTR_MEDIAWIKI_NAMESPACES";
    protected static final String ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES = "ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES";

    private static Logger logger = LogManager.getLogger(NeoBatMediaWiki.class);

    protected XOWAParser xowaParser;

    protected NeoBatMediaWiki(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatMediaWiki(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
    }

    @Override
    public String getSiteName() {
        return (String)getProperty(ATTR_MEDIAWIKI_SITENAME, null);
    }

    @Override
    public void setSiteName(String pSiteName) {
        setProperty(ATTR_MEDIAWIKI_SITENAME, pSiteName);
    }

    @Override
    public String getDbName() {
        return (String)getProperty(ATTR_MEDIAWIKI_DBNAME, null);
    }

    @Override
    public Map<String, Namespace> getNamespaceMap() {
        Map<String, Namespace> lResult = new HashMap<>();
        for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
            Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
            for (String lName:lNamespace.getAllNames()) {
                lResult.put(lName, lNamespace);
            }
        }
        return lResult;
    }

    @Override
    public Map<Integer, Namespace> getNamespaceIdMap() {
        Map<Integer, Namespace> lResult = new HashMap<>();
        for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
            Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
            lResult.put(lNamespace.getId(), lNamespace);
        }
        return lResult;
    }

    public Set<Namespace> getNamespaces() {
        Set<Namespace> lResult = new HashSet<>();
        for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
            Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
            lResult.add(lNamespace);
        }
        return lResult;
    }

    @Override
    public Namespace getNamespace(String pName) {
        if (pName == null) return null;
        Long lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatNamespace.ATTR_NAMESPACE_ALLNAMES, pName)) {
            lResult = lNode;
        }
        Namespace lNamespace = lResult == null ? null : (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lResult);
        return lNamespace;
    }

    @Override
    public Namespace getNamespace(int pNamespaceId) {
        Long lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatNamespace.ATTR_NAMESPACE_ID, pNamespaceId)) {
            lResult = lNode;
        }
        Namespace lNamespace = lResult == null ? null : (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lResult);
        return lNamespace;
    }

    @Override
    public Namespace createNamespace(int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException {
        return NeoBatNamespace.create(this, pId, pCase, pName, pCanonicalName, pAliases, pSubPages, pDefaultContentModel);
    }

    @Override
    public Page createPage(long pId, Namespace pNeoNamespace, String pTitle) throws WikiDragonException {
        return NeoBatPage.create(this, pId, (NeoBatNamespace)pNeoNamespace, pTitle);
    }

    @Override
    public Page getPage(long pId) {
        Page lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatPage.ATTR_PAGE_ID, pId)) {
            assert lResult == null;
            lResult = (Page)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public Page getPage(int pNamespaceId, String pTitle) {
        Page lResult = null;
        Namespace lNamespace = getNamespace(pNamespaceId);
        if (lNamespace != null) {
            lResult = lNamespace.getPage(pTitle);
        }
        return lResult;
    }

    @Override
    public Iterable<Contributor> getContributors() {
        return new Iterable<Contributor>() {
            @Override
            public Iterator<Contributor> iterator() {
                return getContributorIterator();
            }
        };
    }

    @Override
    public WikiObjectIterator<Contributor> getContributorIterator() {
        return new NeoBatWikiObjectIterator<Contributor>(wikiDragonDatabase, wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoBatPage.ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.CONTRIBUTOR.name()));
    }

    @Override
    public java.lang.Iterable<Page> getPages() {
        return new Iterable<Page>() {
            @Override
            public Iterator<Page> iterator() {
                return getPageIterator();
            }
        };
    }

    @Override
    public List<Page> getPagesList() {
        List<Page> lResult = new ArrayList<>();
        for (Page lPage:getPages()) {
            lResult.add(lPage);
        }
        return lResult;
    }

    @Override
    public WikiObjectIterator<Page> getPageIterator() {
        return new NeoBatWikiObjectIterator<Page>(wikiDragonDatabase, wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoBatPage.ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.PAGE.name()));
    }

    @Override
    public Revision getRevision(long pId) {
        Revision lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatRevision.ATTR_REVISION_ID, pId)) {
            assert lResult == null;
            lResult = (Revision)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public String getBase() {
        return (String)getProperty(ATTR_MEDIAWIKI_BASE, null);
    }

    @Override
    public void setBase(String pBase) {
        setProperty(ATTR_MEDIAWIKI_BASE, pBase);
    }

    @Override
    public String getGenerator() {
        return (String)getProperty(ATTR_MEDIAWIKI_GENERATOR, null);
    }

    @Override
    public void setGenerator(String pGenerator) {
        setProperty(ATTR_MEDIAWIKI_GENERATOR, pGenerator);
    }

    @Override
    public MediaWikiConst.Case getCase() {
        String lString = (String)getProperty(ATTR_MEDIAWIKI_CASE, null);
        return lString == null ? null : MediaWikiConst.Case.valueOf(lString);
    }

    @Override
    public void setCase(MediaWikiConst.Case pCase) {
        setProperty(ATTR_MEDIAWIKI_CASE, pCase.name());
    }

    @Override
    public String getApiUrl() {
        return (String)getProperty(ATTR_MEDIAWIKI_APIURL, null);
    }

    @Override
    public void setApiUrl(String pApiUrl) {
        setProperty(ATTR_MEDIAWIKI_APIURL, pApiUrl);
    }

    @Override
    public Contributor getContributor(String pName) {
        Contributor lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatContributor.ATTR_CONTRIBUTOR_NAME, pName)) {
            assert lResult == null;
            lResult = (Contributor)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public Contributor getContributor(long pId) {
        Contributor lResult = null;
        for (Long lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node).get(NeoBatContributor.ATTR_CONTRIBUTOR_ID, pId)) {
            assert lResult == null;
            lResult = (Contributor)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public Contributor createContributor(String pName, long pId) throws WikiDragonException {
        return NeoBatContributor.create(this, pName, pId);
    }

    public synchronized XOWAParser getXOWAParser() throws WikiDragonException {
        if (xowaParser == null) {
            xowaParser = new XOWAParser(this);
        }
        return xowaParser;
    }

    @Override
    public void importWikiDataEntityUsageDump(File pInputFile) throws WikiDragonException {
        try {
            Map<String, WikiDataEntity> lEntityMap = new HashMap<>();
            Map<Long, Page> lPageMap = new HashMap<>();
            SQLTupelIterator i = new SQLTupelIterator(IOUtil.getInputStream(pInputFile), "UTF-8");
            long lLinkCounter = 0;
            long lImportedLinkCounter = 0;
            while (i.hasNext()) {
                if (lLinkCounter % 100000 == 0) {
                    logger.info("Importing Links. Parsed=" + lLinkCounter + ", Imported=" + lImportedLinkCounter);
                }
                lLinkCounter++;
                String[] lFields = i.next();
                String lEntityID = lFields[1];
                String lAspect = lFields[2];
                long lPageID = Long.parseLong(lFields[3]);
                Page lPage = lPageMap.get(lPageID);
                if (lPage == null) {
                    lPage = getPage(lPageID);
                    if (lPage != null) {
                        lPageMap.put(lPageID, lPage);
                    }
                }
                if (lPage != null) {
                    WikiDataEntity lEntity = lEntityMap.get(lEntityID);
                    if (lEntity == null) {
                        lEntity = wikiDragonDatabase.getWikiDataEntity(lEntityID);
                    }
                    if (lEntity == null) {
                        lEntity = wikiDragonDatabase.createWikiDataEntity(lEntityID);
                        lEntityMap.put(lEntityID, lEntity);
                    }
                    lPage.createAspectWikiDataEntityRelation(lEntity, lAspect);
                }
            }
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

    @Override
    public void importLinkDump(File pInputFile, MediaWikiConst.LinkType pLinkType) throws WikiDragonException {
        try {
            TLongObjectHashMap<Page> lPageMap = new TLongObjectHashMap<>();
            Map<String, Page> lTitleMap = new HashMap<>();
            SQLTupelIterator i = new SQLTupelIterator(IOUtil.getInputStream(pInputFile), "UTF-8");
            Page lPrevPage = null;
            long lPrevPageID = -1;
            Set<Page> lLinkedPages = new HashSet<>();
            long lLinkCounter = 0;
            long lImportedLinkCounter = 0;
            while (i.hasNext()) {
                if (lLinkCounter % 100000 == 0) {
                    logger.info("Importing Links. Parsed="+lLinkCounter+", Imported="+lImportedLinkCounter);
                }
                lLinkCounter++;
                String[] lFields = i.next();
                Page lCurrentPage = null;
                long lCurrentPageID = -1;
                String lTargetTitle = null;
                int lTargetNamespaceID = -1;
                switch (pLinkType) {
                    case ARTICLE: {
                        lCurrentPageID = Long.parseLong(lFields[0]);
                        if (lCurrentPageID == lPrevPageID) {
                            lCurrentPage = lPrevPage;
                        }
                        else {
                            lCurrentPage = lPageMap.get(lCurrentPageID);
                            if (lCurrentPage == null) {
                                lCurrentPage = getPage(lCurrentPageID);
                                if (lCurrentPage != null) {
                                    lPageMap.put(lCurrentPageID, lCurrentPage);
                                }
                            }
                        }
                        lTargetNamespaceID = Integer.parseInt(lFields[1]);
                        lTargetTitle = lFields[2].replace("_", " ");
                        break;
                    }
                    case CATEGORIZATION: {
                        lCurrentPageID = Long.parseLong(lFields[0]);
                        if (lCurrentPageID == lPrevPageID) {
                            lCurrentPage = lPrevPage;
                        }
                        else {
                            lCurrentPage = lPageMap.get(lCurrentPageID);
                            if (lCurrentPage == null) {
                                lCurrentPage = getPage(lCurrentPageID);
                                if (lCurrentPage != null) {
                                    lPageMap.put(lCurrentPageID, lCurrentPage);
                                }
                            }
                        }
                        lTargetNamespaceID = 14;
                        lTargetTitle = lFields[1].replace("_", " ");
                        break;
                    }
                    case REDIRECT: {
                        lCurrentPageID = Long.parseLong(lFields[0]);
                        if (lCurrentPageID == lPrevPageID) {
                            lCurrentPage = lPrevPage;
                        }
                        else {
                            lCurrentPage = lPageMap.get(lCurrentPageID);
                            if (lCurrentPage == null) {
                                lCurrentPage = getPage(lCurrentPageID);
                                if (lCurrentPage != null) {
                                    lPageMap.put(lCurrentPageID, lCurrentPage);
                                }
                            }
                        }
                        lTargetNamespaceID = Integer.parseInt(lFields[1]);
                        lTargetTitle = lFields[2].replace("_", " ");
                        if (lFields[3].length() > 0) {
                            // Skip InterWiki Links
                            lTargetTitle = null;
                        }
                        break;
                    }
                }
                if ((lPrevPage != null) && (lPrevPage != lCurrentPage)) {
                    if (lLinkedPages.size() > 0) {
                        Map<MediaWikiConst.LinkType, Set<Page>> lMap = new HashMap<>();
                        lMap.put(pLinkType, lLinkedPages);
                        ((NeoBatPage)lPrevPage).addWikiPageLinksOut(WikiDragonConst.NULLDATETIME, lMap);
                    }
                    lLinkedPages.clear();
                }
                if (lTargetTitle != null) {
                    Page lTargetPage = lTitleMap.get(lTargetNamespaceID+"\t"+lTargetTitle);
                    if (lTargetPage == null) {
                        lTargetPage = getPage(lTargetNamespaceID, lTargetTitle);
                        if (lTargetPage != null) {
                            lTitleMap.put(lTargetNamespaceID+"\t"+lTargetTitle, lTargetPage);
                        }
                    }
                    if (lTargetPage != null) {
                        lImportedLinkCounter++;
                        lLinkedPages.add(lTargetPage);
                    }
                }
                lPrevPage = lCurrentPage;
                lPrevPageID = lCurrentPageID;
            }
            if (lPrevPage != null) {
                if (lLinkedPages.size() > 0) {
                    Map<MediaWikiConst.LinkType, Set<Page>> lMap = new HashMap<>();
                    lMap.put(pLinkType, lLinkedPages);
                    ((NeoBatPage)lPrevPage).addWikiPageLinksOut(WikiDragonConst.NULLDATETIME, lMap);
                }
                lLinkedPages.clear();
            }
            // Store that we have extracted this kind of information
            addWikiPageLinkAvailable(WikiDragonConst.NULLDATETIME, pLinkType);
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

    @Override
    public void createPageTiersHTML(ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter) throws WikiDragonException {
        XOWATierMassParser lXOWATierMassParser = new XOWATierMassParser(this, pTimestamp, pXOWATierMassParserFilter);
        lXOWATierMassParser.parse();
    }

    @Override
    public void createPageTiersHTML(ZonedDateTime pTimestamp, XOWATierMassParserFilter pXOWATierMassParserFilter, int pMaxThreads) throws WikiDragonException {
        XOWATierMassParser lXOWATierMassParser = new XOWATierMassParser(this, pTimestamp, pXOWATierMassParserFilter, pMaxThreads);
        lXOWATierMassParser.parse();
    }

    @Override
    public boolean isPageTierNetworkExtracted(ZonedDateTime pTimestamp) throws WikiDragonException {
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        String lKey = MediaWikiConst.LinkType.ARTICLE.name()+"\t"+lTimestampUTCString;
        String[] lFields = (String[])getProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, new String[0]);
        for (String lField:lFields) {
            if (lField.equals(lKey)) return true;
        }
        return false;
    }

    @Override
    public Page getPage(String pQualifiedTitle) {
        if ((pQualifiedTitle.indexOf(":") > 0) && (pQualifiedTitle.indexOf(":") < pQualifiedTitle.length()-1)) {
            Namespace lNamespace = getNamespace(pQualifiedTitle.substring(0, pQualifiedTitle.indexOf(":")));
            if (lNamespace != null) {
                return lNamespace.getPage(pQualifiedTitle.substring(pQualifiedTitle.indexOf(":")+1));
            }
        }
        return getPage(0, pQualifiedTitle);
    }

    @Override
    public List<ZonedDateTime> getPageTierNetworkExtractedTimestamps() throws WikiDragonException {
        TreeSet<ZonedDateTime> lResult = new TreeSet<>();
        String[] lFields = (String[])getProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, new String[0]);
        for (String lField:lFields) {
            lResult.add(ZonedDateTime.parse(lField.substring(lField.indexOf("\t")+1)));
        }
        return new ArrayList<>(lResult);
    }

    @Override
    public void extractPageTierNetwork(ZonedDateTime pTimestamp) throws WikiDragonException {
        long lPageCounter = 0;
        Set<Page> lPages = new HashSet<>();
        for (Page lPage:getPages()) {
            lPages.add(lPage);
        }
        Map<Integer, Map<String, Page>> lCacheMap = new HashMap<>();
        for (Page lPage:lPages) {
            try (WikiTransaction tx = getWikiDragonDatabase().beginTx()) {
                lPageCounter++;
                logger.info("Extracting PageTier Network: " + lPageCounter + " pages parsed");
                PageTier lPageTier = lPage.getPageTierAt(pTimestamp);
                if (lPageTier != null) {
                    String lHtml = lPageTier.getTierAttribute(PageTier.TierAttribute.HTML);
                    Set<WikiPageLink> lLinks = HTMLWikiLinkExtraction.extractWikiPageLinks(this, lPage, lHtml, pTimestamp, lCacheMap);
                    Map<MediaWikiConst.LinkType, Set<Page>> lMap = new HashMap<>();
                    for (MediaWikiConst.LinkType lType : MediaWikiConst.LinkType.values()) {
                        lMap.put(lType, new HashSet<>());
                    }
                    for (WikiPageLink lLink : lLinks) {
                        lMap.get(lLink.getLinkType()).add(lLink.getTarget());
                    }
                    ((NeoBatPage) lPage).addWikiPageLinksOut(pTimestamp, lMap);
                }
                tx.success();
            }
        }
    }

    @Override
    public Set<Page> getCategorizedPages(boolean pRecursive, Page... pCategoryPage) throws WikiDragonException {
        Set<Page> lResult = new HashSet<>();
        if (!pRecursive) {
            for (Page lSeed:pCategoryPage) {
                for (WikiPageLink lLink : lSeed.getWikiPageLinksIn(MediaWikiConst.LinkType.CATEGORIZATION)) {
                    lResult.add(lLink.getSource());
                }
            }
        }
        else {
            List<Page> lQueue = new ArrayList<>();
            Set<Page> lKnown = new HashSet<>();
            for (Page lSeed:pCategoryPage) {
                lQueue.add(0, lSeed);
            }
            lKnown.addAll(lQueue);
            while (lQueue.size() > 0) {
                Page lPage = lQueue.remove(lQueue.size()-1);
                for (WikiPageLink lLink : lPage.getWikiPageLinksIn(MediaWikiConst.LinkType.CATEGORIZATION)) {
                    Page lSub = lLink.getSource();
                    if (!lKnown.contains(lSub)) {
                        lKnown.add(lSub);
                        lResult.add(lSub);
                        lQueue.add(0, lSub);
                    }
                }
            }
        }
        return lResult;
    }

    /**
     * Intended to be used by the importsql dumps since we have separate types there..
     * @param pTimestamp
     * @param pLinkType
     */
    protected void addWikiPageLinkAvailable(ZonedDateTime pTimestamp, MediaWikiConst.LinkType pLinkType) {
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        String lKey = pLinkType.name()+"\t"+lTimestampUTCString;
        String[] lFields = (String[])getProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, new String[0]);
        TreeSet<String> lSet = new TreeSet<>();
        for (String lString:lFields) {
            lSet.add(lString);
        }
        lSet.add(lKey);
        if (lFields.length != lSet.size()) {
            lFields = new String[lSet.size()];
            int i=0;
            for (String lString:lSet) {
                lFields[i++] = lString;
            }
            setProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, lFields);
        }
    }

    protected Set<MediaWikiConst.LinkType> getWikiPageLinkAvailable(ZonedDateTime pTimestamp) {
        Set<MediaWikiConst.LinkType> lResult = new HashSet<>();
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        String[] lFields = (String[])getProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, new String[0]);
        for (String lString:lFields) {
            if (lString.endsWith(lTimestampUTCString)) {
                lResult.add(MediaWikiConst.LinkType.valueOf(lString.substring(0, lString.indexOf("\t"))));
            }
        }
        return lResult;
    }

    protected boolean isWikiPageLinkAvailable(ZonedDateTime pTimestamp, MediaWikiConst.LinkType pLinkType) {
        String lTimestampUTCString = StringUtil.zonedDateTime2String(pTimestamp.withZoneSameInstant(ZoneId.of("UTC")));
        String lKey = pLinkType.name()+"\t"+lTimestampUTCString;
        String[] lFields = (String[])getProperty(ATTR_MEDIAWIKI_WIKIPAGELINKSTOREDTYPES, new String[0]);
        for (String lString:lFields) {
            if (lString.equals(lKey)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        switch (pProperty) {
            case ATTR_MEDIAWIKI_SITENAME: return true;
            case ATTR_MEDIAWIKI_DBNAME: return true;
            case ATTR_MEDIAWIKI_BASE: return true;
            case ATTR_MEDIAWIKI_GENERATOR: return true;
            case ATTR_MEDIAWIKI_CASE: return true;
            case ATTR_MEDIAWIKI_APIURL: return true;
            default: return false;
        }
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        return false;
    }

    protected static NeoBatMediaWiki create(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, String pDbName) throws WikiDragonException {
        NeoBatMediaWikiCollection lNeoBatMediaWikiCollection = (NeoBatMediaWikiCollection)pNeoBatWikiDragonDatabase.getMediaWikiCollection();
        if (lNeoBatMediaWikiCollection.getMediaWiki(pDbName) != null) throw new WikiDragonException("MediaWiki with name '"+pDbName+"' already exists in collection");
        NeoBatMediaWiki lResult = null;
        long lNode = pNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
        lResult = (NeoBatMediaWiki) pNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.MEDIAWIKI);
        lResult.disableAutosaveOnce();
        lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.MEDIAWIKI.name());
        lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lNode);
        lResult.setProperty(ATTR_MEDIAWIKI_DBNAME, pDbName);
        lResult.saveProperties();
        TLongHashSet lMediaWikis = new TLongHashSet((long[])lNeoBatMediaWikiCollection.getProperty(NeoBatMediaWikiCollection.ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, new long[0]));
        lMediaWikis.add(lNode);
        lNeoBatMediaWikiCollection.disableAutosaveOnce();
        lNeoBatMediaWikiCollection.setProperty(NeoBatMediaWikiCollection.ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, lMediaWikis.toArray());
        lNeoBatMediaWikiCollection.saveProperties();
        return lResult;
    }

}
