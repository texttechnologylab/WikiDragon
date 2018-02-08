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
import org.hucompute.wikidragon.core.model.WikiDataEntity;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * @author Rüdiger Gleim
 */
public class NeoWikiDataEntity extends NeoWikiObject implements WikiDataEntity {

    protected static final String ATTR_WIKIDATAENTITY_ENTITYID = "ATTR_WIKIDATAENTITY_ENTITYID";
    protected static final String ATTR_WIKIDATAENTITY_ASPECT = "ATTR_WIKIDATAENTITY_ASPECT";

    protected NeoWikiDataEntity(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        super(pNeoWikiDragonDatabase, pNode);
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            case ATTR_WIKIDATAENTITY_ENTITYID: return true;
            default: return false;
        }
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        return false;
    }

    protected static NeoWikiDataEntity create(NeoWikiDragonDatabase pNeoWikiDragonDatabase, String pEntityID) throws WikiDragonException {
        try (Transaction tx = pNeoWikiDragonDatabase.database.beginTx()) {
            NeoWikiDataEntity lResult = null;
            // Check if it already exists
            if (pNeoWikiDragonDatabase.getWikiDataEntity(pEntityID) != null)
                throw new WikiDragonException("WikiDataEntity with entityID '" + pEntityID + "' already exists");
            Node lNode = pNeoWikiDragonDatabase.database.createNode();
            lResult = (NeoWikiDataEntity) pNeoWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoWikiDragonDatabase.NodeType.WIKIDATAENTITY);
            lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoWikiDragonDatabase.NodeType.WIKIDATAENTITY.name());
            tx.success();
            return lResult;
        }
    }

}
