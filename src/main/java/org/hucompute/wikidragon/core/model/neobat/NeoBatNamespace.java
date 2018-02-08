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
import org.neo4j.graphdb.index.IndexHits;

import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatNamespace extends NeoBatWikiObject implements Namespace {

    private static Logger logger = LogManager.getLogger(NeoBatNamespace.class);

    protected static final String ATTR_NAMESPACE_ID = "ATTR_NAMESPACE_ID";
    protected static final String ATTR_NAMESPACE_CASE = "ATTR_NAMESPACE_CASE";
    protected static final String ATTR_NAMESPACE_NAME = "ATTR_NAMESPACE_NAME";
    protected static final String ATTR_NAMESPACE_CANONICALNAME = "ATTR_NAMESPACE_CANONICALNAME";
    protected static final String ATTR_NAMESPACE_ALIASES = "ATTR_NAMESPACE_ALIASES";
    protected static final String ATTR_NAMESPACE_ALLNAMES = "ATTR_NAMESPACE_ALLNAMES";
    protected static final String ATTR_NAMESPACE_SUBPAGES = "ATTR_NAMESPACE_SUBPAGES";
    protected static final String ATTR_NAMESPACE_DEFAULTCONTENTMODEL = "ATTR_NAMESPACE_DEFAULTCONTENTMODEL";

    protected NeoBatNamespace(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatNamespace(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
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
        List<Page> lResult = new ArrayList<>();
        for (Page lPage:getPages()) {
            lResult.add(lPage);
        }
        return lResult;
    }

    @Override
    public WikiObjectIterator<Page> getPageIterator() {
        return new NeoBatWikiObjectIterator<>(wikiDragonDatabase, ((NeoBatWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).get(NeoBatPage.ATTR_PAGE_NAMESPACEID, getId()));
    }

    @Override
    public Page getPage(String pTitle) {
        Page lResult = null;
        pTitle = getNormalizedPageTitle(pTitle);
        BooleanQuery lBooleanQuery = new BooleanQuery.Builder().add(new BooleanClause(new TermQuery(new Term(NeoBatPage.ATTR_PAGE_TITLE, pTitle)), BooleanClause.Occur.MUST))
                .add(new BooleanClause(new TermQuery(new Term(NeoBatPage.ATTR_PAGE_NAMESPACEID, Integer.toString(getId()))), BooleanClause.Occur.MUST)).build();
        // TODO: Use getSingle() instead of the loop method
        //Long lNode = ((NeoBatWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery).getSingle();
        Long lNode = null;
        IndexHits<Long> lHits = ((NeoBatWikiDragonDatabase)wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).query(lBooleanQuery);
        while (lHits.hasNext()) {
            if (lNode != null) {
                logger.warn("Duplicate Index Entry for: "+pTitle);
                break;
            }
            lNode = lHits.next();
        }
        lHits.close();
        if (lNode != null) {
            lResult = (Page)((NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory()).getWikiObject(lNode);
        }
        return lResult;
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

    protected static NeoBatNamespace create(NeoBatMediaWiki pNeoBatMediaWiki, int pId, MediaWikiConst.Case pCase, String pName, String pCanonicalName, Set<String> pAliases, boolean pSubPages, MediaWikiConst.Model pDefaultContentModel) throws WikiDragonException {
        NeoBatWikiDragonDatabase lNeoBatWikiDragonDatabase = pNeoBatMediaWiki.wikiDragonDatabase;
        NeoBatNamespace lResult = null;
        // Check if it already exists
        if (pNeoBatMediaWiki.getNamespace(pName) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pName+"' already exists");
        if (pNeoBatMediaWiki.getNamespace(pCanonicalName) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pCanonicalName+"' already exists");
        for (String lAlias:pAliases) {
            if (pNeoBatMediaWiki.getNamespace(lAlias) != null) throw new WikiDragonException("Namespace with name, canonical name or alias '"+pCanonicalName+"' already exists");
        }
        long lNode = lNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
        lResult = (NeoBatNamespace)lNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.NAMESPACE);
        lResult.disableAutosaveOnce();
        lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.NAMESPACE.name());
        lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, pNeoBatMediaWiki.node);
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
        TLongHashSet lNamespaces = new TLongHashSet((long[])pNeoBatMediaWiki.getProperty(NeoBatMediaWiki.ATTR_MEDIAWIKI_NAMESPACES, new long[0]));
        lNamespaces.add(lNode);
        pNeoBatMediaWiki.disableAutosaveOnce();
        pNeoBatMediaWiki.setProperty(NeoBatMediaWiki.ATTR_MEDIAWIKI_NAMESPACES, lNamespaces.toArray());
        pNeoBatMediaWiki.saveProperties();
        lResult.saveProperties();
        return lResult;
    }
}
