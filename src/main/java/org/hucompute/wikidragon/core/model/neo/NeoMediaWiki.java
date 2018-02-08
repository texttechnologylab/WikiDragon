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

import gnu.trove.set.hash.TLongHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.parsing.XOWAParser;
import org.hucompute.wikidragon.core.parsing.XOWATierMassParser;
import org.hucompute.wikidragon.core.parsing.filter.XOWATierMassParserFilter;
import org.hucompute.wikidragon.core.util.IOUtil;
import org.hucompute.wikidragon.core.util.SQLTupelIterator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoMediaWiki extends NeoWikiObject implements MediaWiki {

    protected static final String ATTR_MEDIAWIKI_DBNAME = "ATTR_MEDIAWIKI_DBNAME";
    protected static final String ATTR_MEDIAWIKI_SITENAME = "ATTR_MEDIAWIKI_SITENAME";
    protected static final String ATTR_MEDIAWIKI_BASE = "ATTR_MEDIAWIKI_BASE";
    protected static final String ATTR_MEDIAWIKI_GENERATOR = "ATTR_MEDIAWIKI_GENERATOR";
    protected static final String ATTR_MEDIAWIKI_CASE = "ATTR_MEDIAWIKI_CASE";
    protected static final String ATTR_MEDIAWIKI_APIURL = "ATTR_MEDIAWIKI_APIURL";
    protected static final String ATTR_MEDIAWIKI_NAMESPACES = "ATTR_MEDIAWIKI_NAMESPACES";

    private static Logger logger = LogManager.getLogger(NeoMediaWiki.class);

    protected XOWAParser xowaParser;

    protected NeoMediaWiki(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
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
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Map<String, Namespace> lResult = new HashMap<>();
            for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
                Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
                for (String lName:lNamespace.getAllNames()) {
                    lResult.put(lName, lNamespace);
                }
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Map<Integer, Namespace> getNamespaceIdMap() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Map<Integer, Namespace> lResult = new HashMap<>();
            for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
                Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
                lResult.put(lNamespace.getId(), lNamespace);
            }
            tx.success();
            return lResult;
        }
    }

    public Set<Namespace> getNamespaces() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Set<Namespace> lResult = new HashSet<>();
            for (long l:(long[])getProperty(ATTR_MEDIAWIKI_NAMESPACES, new long[0])) {
                Namespace lNamespace = (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l);
                lResult.add(lNamespace);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Namespace getNamespace(String pName) {
        if (pName == null) return null;
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Node lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoNamespace.ATTR_NAMESPACE_ALLNAMES, pName)) {
                lResult = lNode;
            }
            Namespace lNamespace = lResult == null ? null : (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lResult);
            tx.success();
            return lNamespace;
        }
    }

    @Override
    public Namespace getNamespace(int pNamespaceId) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Node lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoNamespace.ATTR_NAMESPACE_ID, pNamespaceId)) {
                lResult = lNode;
            }
            Namespace lNamespace = lResult == null ? null : (Namespace)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lResult);
            tx.success();
            return lNamespace;
        }
    }

    @Override
    public Namespace createNamespace(int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException {
        return NeoNamespace.create(this, pId, pCase, pName, pCanonicalName, pAliases, pSubPages, pDefaultContentModel);
    }

    @Override
    public Page createPage(long pId, Namespace pNamespace, String pTitle) throws WikiDragonException {
        return NeoPage.create(this, pId, (NeoNamespace) pNamespace, pTitle);
    }

    @Override
    public Page getPage(long pId) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Page lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoPage.ATTR_PAGE_ID, pId)) {
                assert lResult == null;
                lResult = (Page)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Page getPage(int pNamespaceId, String pTitle) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Page lResult = null;
            Namespace lNamespace = getNamespace(pNamespaceId);
            if (lNamespace != null) {
                lResult = lNamespace.getPage(pTitle);
            }
            tx.success();
            return lResult;
        }
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
        Transaction tx = wikiDragonDatabase.database.beginTx();
        return new NeoWikiObjectIterator<Contributor>(wikiDragonDatabase, wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoPage.ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.CONTRIBUTOR.name()), tx);
    }

    @Override
    public Iterable<Page> getPages() {
        return new Iterable<Page>() {
            @Override
            public Iterator<Page> iterator() {
                return getPageIterator();
            }
        };
    }

    @Override
    public List<Page> getPagesList() {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            List<Page> lResult = new ArrayList<>();
            for (Page lPage:getPages()) {
                lResult.add(lPage);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public WikiObjectIterator<Page> getPageIterator() {
        Transaction tx = wikiDragonDatabase.database.beginTx();
        return new NeoWikiObjectIterator<Page>(wikiDragonDatabase, wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).get(NeoPage.ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.PAGE.name()), tx);
    }

    @Override
    public Revision getRevision(long pId) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Revision lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoRevision.ATTR_REVISION_ID, pId)) {
                assert lResult == null;
                lResult = (Revision)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
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
    public Contributor getContributor(String pName) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Contributor lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoContributor.ATTR_CONTRIBUTOR_NAME, pName)) {
                assert lResult == null;
                lResult = (Contributor)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Contributor getContributor(long pId) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Contributor lResult = null;
            for (Node lNode:wikiDragonDatabase.getMediaWikiNodeIndex(node.getId()).get(NeoContributor.ATTR_CONTRIBUTOR_ID, pId)) {
                assert lResult == null;
                lResult = (Contributor)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    @Override
    public Contributor createContributor(String pName, long pId) throws WikiDragonException {
        return NeoContributor.create(this, pName, pId);
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
        return false;
    }

    @Override
    public List<ZonedDateTime> getPageTierNetworkExtractedTimestamps() throws WikiDragonException {
        return null;
    }

    @Override
    public void extractPageTierNetwork(ZonedDateTime pTimestamp) throws WikiDragonException {

    }

    @Override
    public Set<Page> getCategorizedPages(boolean pRecursive, Page... pCategoryPage) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
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
            tx.success();
            return lResult;
        }
    }

    public void importWikiDataEntityUsageDump(File pInputFile) throws WikiDragonException {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
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
            tx.success();
        }
        catch (IOException e) {
            throw new WikiDragonException(e.getMessage(), e);
        }
    }

    public synchronized XOWAParser getXOWAParser() throws WikiDragonException {
        if (xowaParser == null) {
            xowaParser = new XOWAParser(this);
        }
        return xowaParser;
    }

    @Override
    public void importLinkDump(File pInputFile, MediaWikiConst.LinkType pLinkType) throws WikiDragonException {
        throw new WikiDragonException("Not implemented");
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

    protected static NeoMediaWiki create(NeoWikiDragonDatabase pNeoWikiDragonDatabase, String pDbName) throws WikiDragonException {
        try (Transaction tx = pNeoWikiDragonDatabase.database.beginTx()) {
            NeoMediaWikiCollection lNeoMediaWikiCollection = (NeoMediaWikiCollection)pNeoWikiDragonDatabase.getMediaWikiCollection();
            if (lNeoMediaWikiCollection.getMediaWiki(pDbName) != null) throw new WikiDragonException("MediaWiki with name '"+pDbName+"' already exists in collection");
            NeoMediaWiki lResult = null;
            Node lNode = pNeoWikiDragonDatabase.database.createNode();
            lResult = (NeoMediaWiki) pNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.MEDIAWIKI);
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, lNode.getId());
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.MEDIAWIKI.name());
            lResult.setProperty(ATTR_MEDIAWIKI_DBNAME, pDbName);
            TLongHashSet lMediaWikis = new TLongHashSet((long[])lNeoMediaWikiCollection.node.getProperty(NeoMediaWikiCollection.ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, new long[0]));
            lMediaWikis.add(lNode.getId());
            lNeoMediaWikiCollection.node.setProperty(NeoMediaWikiCollection.ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, lMediaWikis.toArray());
            tx.success();
            return lResult;
        }
    }

}
