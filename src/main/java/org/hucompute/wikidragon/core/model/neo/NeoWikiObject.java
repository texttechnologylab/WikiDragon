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
import org.hucompute.wikidragon.core.model.WikiDragonDatabase;
import org.hucompute.wikidragon.core.model.WikiObject;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 * @author Rüdiger Gleim
 */
public abstract class NeoWikiObject implements WikiObject {

    private static Logger logger = LogManager.getLogger(NeoWikiObject.class);

    protected static final long GLOBALINDEXID = -1;

    protected static final String ATTR_WIKIOBJECT_MEDIAWIKINODEID = "ATTR_WIKIOBJECT_MEDIAWIKINODEID";
    protected static final String ATTR_WIKIOBJECT_TYPE = "ATTR_WIKIOBJECT_TYPE";

    protected NeoWikiDragonDatabase wikiDragonDatabase;
    protected Node node;

    private NeoWikiDragonDatabase.NodeType cachedNodeType;
    private long cachedMediaWikiId = -1;

    protected NeoWikiObject(NeoWikiDragonDatabase pNeoWikiDragonDatabase, Node pNode) {
        wikiDragonDatabase = pNeoWikiDragonDatabase;
        node = pNode;
    }

    @Override
    public void delete() {
        node.delete();
    }

    @Override
    public String getUniqueId() {
        return Long.toString(node.getId());
    }

    public NeoWikiDragonDatabase.NodeType getType() {
        if (cachedNodeType == null) {
            cachedNodeType = NeoWikiDragonDatabase.NodeType.valueOf((String)getProperty(ATTR_WIKIOBJECT_TYPE));
        }
        return cachedNodeType;
    }

    @Override
    public WikiDragonDatabase getWikiDragonDatabase() {
        return wikiDragonDatabase;
    }

    /**
     * Get id of MediaWiki to which this Object belongs or GLOBALINDEXID if it does not belong to a MediaWiki
     * @return id of MediaWiki to which this Object belongs or GLOBALINDEXID if it does not belong to a MediaWiki
     */
    protected long getMediaWikiId() {
        if (cachedMediaWikiId == -1) {
            cachedMediaWikiId = (long)getProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID);
        }
        return cachedMediaWikiId;
    }

    protected Object getProperty(String pKey) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Object lResult = node.getProperty(pKey);
            tx.success();
            return lResult;
        }
    }

    protected Object getProperty(String pKey, Object pDefaultValue) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            Object lResult = node.getProperty(pKey, pDefaultValue);
            tx.success();
            return lResult;
        }
    }

    protected void setProperty(String pKey, Object pValue) {
        if (pValue == null) {
            removeProperty(pKey);
        }
        else {
            try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
                if (isIndexedGlobal(pKey)) {
                    wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).remove(node, pKey);
                    wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).add(node, pKey, pValue);
                }
                if (isIndexedMediaWiki(pKey)) {
                    wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).remove(node, pKey);
                    wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).add(node, pKey, pValue);
                }
                node.setProperty(pKey, pValue);
                switch (pKey) {
                    case ATTR_WIKIOBJECT_MEDIAWIKINODEID: {
                        cachedMediaWikiId = (Long)pValue;
                        break;
                    }
                }
                tx.success();
            }
        }
    }

    protected void removeProperty(String pKey) {
        try (Transaction tx = wikiDragonDatabase.database.beginTx()) {
            if (isIndexedGlobal(pKey)) {
                wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).remove(node, pKey);
            }
            if (isIndexedMediaWiki(pKey)) {
                wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).remove(node, pKey);
            }
            node.removeProperty(pKey);
            tx.success();
        }
    }

    protected abstract boolean isIndexedGlobal(String pProperty);

    protected abstract boolean isIndexedMediaWiki(String pProperty);

    @Override
    public int hashCode() {
        return Long.hashCode(node.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NeoWikiObject)) return false;
        return ((NeoWikiObject)obj).node.getId() == node.getId();
    }
}
