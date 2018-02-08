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

import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.Contributor;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.Revision;
import org.hucompute.wikidragon.core.model.WikiObjectIterator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import java.util.Iterator;

/**
 * @author Rüdiger Gleim
 */
public class NeoContributor extends NeoWikiObject implements Contributor {

    protected static final String ATTR_CONTRIBUTOR_ID = "ATTR_CONTRIBUTOR_ID";
    protected static final String ATTR_CONTRIBUTOR_NAME = "ATTR_CONTRIBUTOR_NAME";

    protected NeoContributor(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    public String getName() {
        return (String)getProperty(ATTR_CONTRIBUTOR_NAME, null);
    }

    @Override
    public long getId() {
        return (Long)getProperty(ATTR_CONTRIBUTOR_ID, null);
    }

    @Override
    public Iterable<Revision> getRevisions() {
        return new Iterable<Revision>() {
            @Override
            public Iterator<Revision> iterator() {
                return getRevisionIterator();
            }
        };
    }

    @Override
    public WikiObjectIterator<Revision> getRevisionIterator() {
        Transaction tx = wikiDragonDatabase.database.beginTx();
        return new NeoWikiObjectIterator<>(wikiDragonDatabase, ((NeoWikiDragonDatabase) wikiDragonDatabase).getMediaWikiNodeIndex(getMediaWikiId()).get(NeoRevision.ATTR_REVISION_CONTRIBUTORNODEID, node.getId()), tx);
    }

    @Override
    public MediaWiki getMediaWiki() {
        return (MediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(getMediaWikiId());
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        return false;
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            case ATTR_CONTRIBUTOR_ID: return true;
            case ATTR_CONTRIBUTOR_NAME: return true;
            default: return false;
        }
    }

    protected static NeoContributor create(NeoMediaWiki pNeoMediaWiki, String pUserName, long pId) throws WikiDragonException {
        NeoWikiDragonDatabase lNeoWikiDragonDatabase = pNeoMediaWiki.wikiDragonDatabase;
        try (Transaction tx = lNeoWikiDragonDatabase.database.beginTx()) {
            NeoContributor lResult = null;
            // Check if it already exists
            if (pNeoMediaWiki.getContributor(pId) != null) throw new WikiDragonException("Contributor with Id '"+pId+"' already exists");
            Node lNode = lNeoWikiDragonDatabase.database.createNode();
            lNeoWikiDragonDatabase.getMediaWikiNodeIndex(pNeoMediaWiki.node.getId()).add(lNode, ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.CONTRIBUTOR.name());
            lResult = (NeoContributor) lNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.CONTRIBUTOR);
            lResult.setProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID, pNeoMediaWiki.node.getId());
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.CONTRIBUTOR.name());
            lResult.setProperty(ATTR_CONTRIBUTOR_ID, pId);
            lResult.setProperty(ATTR_CONTRIBUTOR_NAME, pUserName);
            tx.success();
            return lResult;
        }
    }
}
