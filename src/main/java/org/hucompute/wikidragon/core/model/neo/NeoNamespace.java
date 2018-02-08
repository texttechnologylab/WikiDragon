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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.*;
import org.hucompute.wikidragon.core.util.ArrayUtil;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;

import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoNamespace extends NeoWikiObject implements Namespace {

    private static Logger logger = LogManager.getLogger(NeoNamespace.class);

    protected static final String ATTR_NAMESPACE_ID = "ATTR_NAMESPACE_ID";
    protected static final String ATTR_NAMESPACE_CASE = "ATTR_NAMESPACE_CASE";
    protected static final String ATTR_NAMESPACE_NAME = "ATTR_NAMESPACE_NAME";
    protected static final String ATTR_NAMESPACE_CANONICALNAME = "ATTR_NAMESPACE_CANONICALNAME";
    protected static final String ATTR_NAMESPACE_ALIASES = "ATTR_NAMESPACE_ALIASES";
    protected static final String ATTR_NAMESPACE_ALLNAMES = "ATTR_NAMESPACE_ALLNAMES";
    protected static final String ATTR_NAMESPACE_SUBPAGES = "ATTR_NAMESPACE_SUBPAGES";
    protected static final String ATTR_NAMESPACE_DEFAULTCONTENTMODEL = "ATTR_NAMESPACE_DEFAULTCONTENTMODEL";

    protected NeoNamespace(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    public MediaWiki getMediaWiki() {
        return (MediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(getMediaWikiId());
    }

    @Override
    public int getId() {
        return (int)getProperty(ATTR_NAMESPACE_ID, 0);
    }

    @Override
    public MediaWikiConst.Case getCase() {
        String lCaseString = (String)getProperty(ATTR_NAMESPACE_CASE, null);
        return lCaseString == null ? null : MediaWikiConst.Case.valueOf(lCaseString);
    }

    @Override
    public String getName() {
        return (String)getProperty(ATTR_NAMESPACE_NAME, null);
    }

    @Override
    public String getCanonicalName() {
        return (String)getProperty(ATTR_NAMESPACE_CANONICALNAME, null);
    }

    @Override
    public Set<String> getAliases() {
        String[] lArray = (String[])getProperty(ATTR_NAMESPACE_ALIASES, new String[0]);
        Set<String> lSet = new HashSet<>();
        for (String lString:lArray) {
            lSet.add(lString);
        }
        return lSet;
    }

    @Override
    public Set<String> getAllNames() {
        String[] lArray = (String[])getProperty(ATTR_NAMESPACE_ALLNAMES, new String[0]);
        Set<String> lSet = new HashSet<>();
        for (String lString:lArray) {
            lSet.add(lString);
        }
        return lSet;
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
        return new NeoWikiObjectIterator<>(wikiDragonDatabase, ((NeoWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).get(NeoPage.ATTR_PAGE_NAMESPACEID, getId()), tx);
    }

    @Override
    public Page getPage(String pTitle) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            pTitle = getNormalizedPageTitle(pTitle);
            Page lResult = null;
            BooleanQuery lBooleanQuery = new BooleanQuery.Builder().add(new BooleanClause(new TermQuery(new Term(NeoPage.ATTR_PAGE_TITLE, pTitle)), BooleanClause.Occur.MUST))
                    .add(new BooleanClause(new TermQuery(new Term(NeoPage.ATTR_PAGE_NAMESPACEID, Integer.toString(getId()))), BooleanClause.Occur.MUST)).build();
            Node lNode = null;
            try {
                lNode = ((NeoWikiDragonDatabase) wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery).getSingle();
            }
            catch (NoSuchElementException e) {
                logger.error(e.getMessage(), e);
                IndexHits<Node> lIndexHits = ((NeoWikiDragonDatabase) wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery);
                while (lIndexHits.hasNext()) {
                    Node n = lIndexHits.next();
                    Page lPage = (Page)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(n);
                    logger.error(lPage.getId()+"\t"+lPage.getNamespaceID()+"\t"+lPage.getTitle());
                }
                throw (e);
            }
            if (lNode != null) {
                lResult = (Page)((NeoWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
            }
            tx.success();
            return lResult;
        }
    }

    protected String getNormalizedPageTitle(String pTitle) {
        switch (getCase()) {
            case CASE_SENSITIVE: {
                return pTitle;
            }
            case FIRST_LETTER: {
                if ((pTitle == null) || (pTitle.length() == 0)) {
                    return pTitle;
                }
                else if (pTitle.length() == 1) {
                    return pTitle.toUpperCase();
                }
                else {
                    return Character.toUpperCase(pTitle.charAt(0))+pTitle.substring(1);
                }
            }
            default: {
                return pTitle;
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
            case ATTR_NAMESPACE_ID: return true;
            case ATTR_NAMESPACE_CASE: return true;
            case ATTR_NAMESPACE_NAME: return true;
            case ATTR_NAMESPACE_CANONICALNAME: return true;
            case ATTR_NAMESPACE_ALIASES: return true;
            case ATTR_NAMESPACE_ALLNAMES: return true;
            case ATTR_NAMESPACE_SUBPAGES: return true;
            case ATTR_NAMESPACE_DEFAULTCONTENTMODEL: return true;
            default: return false;
        }
    }


    protected static NeoNamespace create(NeoMediaWiki pNeoMediaWiki, int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException {
        NeoWikiDragonDatabase lNeoWikiDragonDatabase = pNeoMediaWiki.wikiDragonDatabase;
        try (Transaction tx = lNeoWikiDragonDatabase.database.beginTx()) {
            NeoNamespace lResult = null;
            // Check if it already exists
            if (pNeoMediaWiki.getNamespace(pName) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pName+"' already exists");
            if (pNeoMediaWiki.getNamespace(pCanonicalName) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pCanonicalName+"' already exists");
            for (String lAlias:pAliases) {
                if (pNeoMediaWiki.getNamespace(lAlias) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pCanonicalName+"' already exists");
            }
            Node lNode = lNeoWikiDragonDatabase.database.createNode();
            lResult = (NeoNamespace)lNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.NAMESPACE);
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, pNeoMediaWiki.node.getId());
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.NAMESPACE.name());
            lResult.setProperty(ATTR_NAMESPACE_ID, pId);
            if (pCase != null) lResult.setProperty(ATTR_NAMESPACE_CASE, pCase.name());
            lResult.setProperty(ATTR_NAMESPACE_NAME, pName);
            if (pCanonicalName != null) lResult.setProperty(ATTR_NAMESPACE_CANONICALNAME, pCanonicalName);
            lResult.setProperty(ATTR_NAMESPACE_ALIASES, ArrayUtil.stringCollection2Array(pAliases));
            Set<String> lAllNames = new HashSet<>();
            if (pName != null) lAllNames.add(pName);
            if (pCanonicalName != null) lAllNames.add(pCanonicalName);
            if (pAliases != null) lAllNames.addAll(pAliases);
            lResult.setProperty(ATTR_NAMESPACE_ALLNAMES, ArrayUtil.stringCollection2Array(lAllNames));
            lResult.setProperty(ATTR_NAMESPACE_SUBPAGES, pSubPages);
            lResult.setProperty(ATTR_NAMESPACE_DEFAULTCONTENTMODEL, pDefaultContentModel.name());
            //
            TLongHashSet lNamespaces = new TLongHashSet((long[])pNeoMediaWiki.node.getProperty(NeoMediaWiki.ATTR_MEDIAWIKI_NAMESPACES, new long[0]));
            lNamespaces.add(lNode.getId());
            pNeoMediaWiki.node.setProperty(NeoMediaWiki.ATTR_MEDIAWIKI_NAMESPACES, lNamespaces.toArray());
            tx.success();
            return lResult;
        }
    }
}
