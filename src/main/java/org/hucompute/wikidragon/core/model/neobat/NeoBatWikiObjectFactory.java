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
import org.hucompute.wikidragon.core.model.WikiObject;
import org.hucompute.wikidragon.core.model.WikiObjectFactory;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.Map;

import static org.hucompute.wikidragon.core.model.WikiDragonConst.NULLNODEID;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatWikiObjectFactory implements WikiObjectFactory {

    private static Logger logger = LogManager.getLogger(NeoBatWikiObjectFactory.class);

    protected NeoBatWikiDragonDatabase wikiDragonDatabase;
    protected GraphDatabaseService database;

    protected NeoBatWikiObjectFactory(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase) {
        wikiDragonDatabase = pNeoBatWikiDragonDatabase;
    }

    @Override
    public WikiObject getWikiObject(String pUniqueId) {
        return getWikiObject(Long.parseLong(pUniqueId));
    }

    public WikiObject getWikiObject(long pNode) {
        return getWikiObject(pNode, null);
    }

    /**
     * Returns a WikiObject based on this Node.
     * @param pNode
     * @return WikiObject based on this Node.
     */
    public WikiObject getWikiObject(long pNode, NeoBatWikiDragonDatabase.NodeType pType) {
        if (pNode == NULLNODEID) return null;
        WikiObject lResult = null;
        NeoBatWikiDragonDatabase.NodeType lType = pType;
        Map<String, Object> lProperties = null;
        if (lType == null) {
            lProperties = wikiDragonDatabase.database.getNodeProperties(pNode);
            lType = NeoBatWikiDragonDatabase.NodeType.valueOf((String) lProperties.get(NeoBatWikiObject.ATTR_WIKIOBJECT_TYPE));
        }
        switch (lType) {
            case MEDIAWIKI_COLLECTION: {
                lResult = new NeoBatMediaWikiCollection(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case MEDIAWIKI: {
                lResult = new NeoBatMediaWiki(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case NAMESPACE: {
                lResult = new NeoBatNamespace(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case PAGE: {
                lResult = new NeoBatPage(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case REVISION: {
                lResult = new NeoBatRevision(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case CONTRIBUTOR: {
                lResult = new NeoBatContributor(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case PAGETIER: {
                lResult = new NeoBatPageTier(wikiDragonDatabase, pNode, lProperties);
                break;
            }
            case WIKIDATAENTITY: {
                lResult = new NeoBatWikiDataEntity(wikiDragonDatabase, pNode);
                break;
            }
        }
        return lResult;
    }

}
