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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hucompute.wikidragon.core.model.WikiObject;
import org.hucompute.wikidragon.core.model.WikiObjectFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * @author Rüdiger Gleim
 */
public class NeoWikiObjectFactory implements WikiObjectFactory {

    private static Logger logger = LogManager.getLogger(NeoWikiObjectFactory.class);

    protected NeoWikiDragonDatabase wikiDragonDatabase;
    protected GraphDatabaseService database;

    protected NeoWikiObjectFactory(NeoWikiDragonDatabase pNeoWikiDragonDatabase) {
        wikiDragonDatabase = pNeoWikiDragonDatabase;
    }

    @Override
    public WikiObject getWikiObject(String pUniqueId) {
        return getWikiObject(Long.parseLong(pUniqueId));
    }

    /**
     * Returns a WikiObject based on this Id.
     * @param pUniqueId
     * @return WikiObject based on this Id
     */
    protected WikiObject getWikiObject(long pUniqueId) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            WikiObject lResult = getWikiObject(wikiDragonDatabase.database.getNodeById(pUniqueId));
            tx.success();
            return lResult;
        }
    }

    protected WikiObject getWikiObject(Node pNode) {
        return getWikiObject(pNode, null);
    }

    /**
     * Returns a WikiObject based on this Node.
     * @param pNode
     * @return WikiObject based on this Node.
     */
    protected WikiObject getWikiObject(Node pNode, NeoWikiDragonDatabase.NodeType pNodeType) {
        if (pNode == null) return null;
        if (pNodeType == null) {
            pNodeType = NeoWikiDragonDatabase.NodeType.valueOf((String) pNode.getProperty(NeoWikiObject.ATTR_WIKIOBJECT_TYPE));
        }
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            WikiObject lResult = null;
            switch (pNodeType) {
                case MEDIAWIKI_COLLECTION: {
                    lResult = new NeoMediaWikiCollection(wikiDragonDatabase, pNode);
                    break;
                }
                case MEDIAWIKI: {
                    lResult = new NeoMediaWiki(wikiDragonDatabase, pNode);
                    break;
                }
                case NAMESPACE: {
                    lResult = new NeoNamespace(wikiDragonDatabase, pNode);
                    break;
                }
                case PAGE: {
                    lResult = new NeoPage(wikiDragonDatabase, pNode);
                    break;
                }
                case REVISION: {
                    lResult = new NeoRevision(wikiDragonDatabase, pNode);
                    break;
                }
                case CONTRIBUTOR: {
                    lResult = new NeoContributor(wikiDragonDatabase, pNode);
                    break;
                }
                case PAGETIER: {
                    lResult = new NeoPageTier(wikiDragonDatabase, pNode);
                    break;
                }
                case WIKIDATAENTITY: {
                    lResult = new NeoWikiDataEntity(wikiDragonDatabase, pNode);
                    break;
                }
            }
            tx.success();
            return lResult;
        }
    }

}
