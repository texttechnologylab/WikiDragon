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
import org.hucompute.wikidragon.core.model.WikiDragonDatabase;
import org.hucompute.wikidragon.core.model.WikiObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public abstract class NeoBatWikiObject implements WikiObject {

    private static Logger logger = LogManager.getLogger(NeoBatWikiObject.class);

    protected static final long GLOBALINDEXID = -1;

    protected static final String ATTR_WIKIOBJECT_MEDIAWIKINODEID = "ATTR_WIKIOBJECT_MEDIAWIKINODEID";
    protected static final String ATTR_WIKIOBJECT_TYPE = "ATTR_WIKIOBJECT_TYPE";

    protected NeoBatWikiDragonDatabase wikiDragonDatabase;
    protected long node;

    protected Map<String, Object> propertyMap;
    protected Set<String> propertyDirtySet;

    private NeoBatWikiDragonDatabase.NodeType cachedNodeType;

    protected boolean autoSaveProperties = true;

    protected NeoBatWikiObject(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        wikiDragonDatabase = pNeoBatWikiDragonDatabase;
        node = pNode;
        propertyMap = pProperties;
        propertyDirtySet = new HashSet<>();
    }

    protected NeoBatWikiObject(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        this(pNeoBatWikiDragonDatabase, pNode, null);
    }

    @Override
    public void delete() {
    }

    @Override
    public String getUniqueId() {
        return Long.toString(node);
    }

    public NeoBatWikiDragonDatabase.NodeType getType() {
        if (cachedNodeType == null) {
            ensurePropertiesLoaded();
            cachedNodeType = NeoBatWikiDragonDatabase.NodeType.valueOf((String)propertyMap.get(ATTR_WIKIOBJECT_TYPE));
        }
        return cachedNodeType;
    }

    @Override
    public WikiDragonDatabase getWikiDragonDatabase() {
        return wikiDragonDatabase;
    }

    protected void ensurePropertiesLoaded() {
        if (propertyMap == null) {
            propertyMap = new HashMap<>(wikiDragonDatabase.database.getNodeProperties(node));
            propertyDirtySet = new HashSet<>();
        }
    }

    /**
     * Get id of MediaWiki to which this Object belongs or GLOBALINDEXID if it does not belong to a MediaWiki
     * @return id of MediaWiki to which this Object belongs or GLOBALINDEXID if it does not belong to a MediaWiki
     */
    protected long getMediaWikiId() {
        ensurePropertiesLoaded();
        return (long)getProperty(ATTR_WIKIOBJECT_MEDIAWIKINODEID);
    }

    protected Object getProperty(String pKey) {
        ensurePropertiesLoaded();
        return propertyMap.get(pKey);
    }

    protected Object getProperty(String pKey, Object pDefaultValue) {
        ensurePropertiesLoaded();
        return propertyMap.getOrDefault(pKey, pDefaultValue);
    }

    protected void setProperty(String pKey, Object pValue) {
        ensurePropertiesLoaded();
        if (pValue == null) {
            removeProperty(pKey);
        }
        else {
            propertyMap.put(pKey, pValue);
            propertyDirtySet.add(pKey);
            if (autoSaveProperties) saveProperties();
        }
    }

    protected void saveProperties() {
        autoSaveProperties = true;
        if (propertyMap != null) {
            // Check wether we can set single properties or if we need to put the entire set
            boolean lPutAll = false;
            boolean lNeedIndexUpdateGlobal = false;
            boolean lNeedIndexUpdateMediaWiki = false;
            for (String lDirty : propertyDirtySet) {
                // Deletion can only be achieved by putting all
                if (!propertyMap.containsKey(lDirty)) {
                    lPutAll = true;
                }
                if (isIndexedGlobal(lDirty)) {
                    lNeedIndexUpdateGlobal = true;
                }
                if (isIndexedMediaWiki(lDirty)) {
                    lNeedIndexUpdateMediaWiki = true;
                }
            }
            if (lPutAll) {
                wikiDragonDatabase.database.setNodeProperties(node, propertyMap);
            } else {
                for (String lDirty : propertyDirtySet) {
                    wikiDragonDatabase.database.setNodeProperty(node, lDirty, propertyMap.get(lDirty));
                }
            }
            if (lNeedIndexUpdateGlobal) {
                Map<String, Object> lIndexMap = new HashMap<>();
                for (Map.Entry<String, Object> lEntry : propertyMap.entrySet()) {
                    if (isIndexedGlobal(lEntry.getKey())) {
                        lIndexMap.put(lEntry.getKey(), lEntry.getValue());
                    }
                }
                wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).updateOrAdd(node, lIndexMap);
            }
            if (lNeedIndexUpdateMediaWiki) {
                Map<String, Object> lIndexMap = new HashMap<>();
                for (Map.Entry<String, Object> lEntry : propertyMap.entrySet()) {
                    if (isIndexedMediaWiki(lEntry.getKey())) {
                        lIndexMap.put(lEntry.getKey(), lEntry.getValue());
                    }
                }
                wikiDragonDatabase.getMediaWikiNodeIndex(getMediaWikiId()).updateOrAdd(node, lIndexMap);
            }
            propertyDirtySet.clear();
        }
    }

    protected void disableAutosaveOnce() {
        autoSaveProperties = false;
    }

    protected void removeProperty(String pKey) {
        ensurePropertiesLoaded();
        propertyMap.remove(pKey);
        propertyDirtySet.add(pKey);
        if (autoSaveProperties) saveProperties();
    }

    protected abstract boolean isIndexedGlobal(String pProperty);

    protected abstract boolean isIndexedMediaWiki(String pProperty);

    @Override
    public int hashCode() {
        return Long.hashCode(node);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NeoBatWikiObject)) return false;
        return ((NeoBatWikiObject)obj).node == node;
    }
}
