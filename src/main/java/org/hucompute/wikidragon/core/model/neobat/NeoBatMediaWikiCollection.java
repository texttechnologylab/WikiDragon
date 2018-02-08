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
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.MediaWikiCollection;
import org.hucompute.wikidragon.core.revcompression.NoneRevisionCompressor;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressor;

import java.io.InputStream;
import java.util.*;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatMediaWikiCollection extends NeoBatWikiObject implements MediaWikiCollection {

    private static Logger logger = LogManager.getLogger(NeoBatMediaWikiCollection.class);

    /**
     * Array of nodes representing MediaWikis. Not indexed.
     */
    protected static final String ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS = "ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS";

    protected NeoBatMediaWikiCollection(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode) {
        super(pNeoBatWikiDragonDatabase, pNode);
    }

    protected NeoBatMediaWikiCollection(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase, long pNode, Map<String, Object> pProperties) {
        super(pNeoBatWikiDragonDatabase, pNode, pProperties);
    }

    @Override
    public MediaWiki getMediaWiki(String pDbName) {
        MediaWiki lResult = null;
        for (long lNode : wikiDragonDatabase.getMediaWikiNodeIndex(GLOBALINDEXID).get(NeoBatMediaWiki.ATTR_MEDIAWIKI_DBNAME, pDbName)) {
            assert lResult == null;
            lResult = (NeoBatMediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode);
        }
        return lResult;
    }

    @Override
    public MediaWiki createMediaWiki(String pDbName) throws WikiDragonException {
        return NeoBatMediaWiki.create(wikiDragonDatabase, pDbName);
    }

    @Override
    public Collection<MediaWiki> getMediaWikis() {
        Set<MediaWiki> lResult = new HashSet<>();
        for (long l:(long[])getProperty(ATTR_MEDIAWIKICOLLECTION_MEDIAWIKIS, new long[0])) {
            lResult.add((NeoBatMediaWiki)wikiDragonDatabase.wikiObjectFactory.getWikiObject(l));
        }
        return lResult;
    }

    @Override
    public MediaWiki importMediaWiki(InputStream pInputStream, String pCharset) throws WikiDragonException {
        return importMediaWiki(pInputStream, pCharset, new NoneRevisionCompressor());
    }

    @Override
    public MediaWiki importMediaWiki(InputStream pInputStream, String pCharset, RevisionCompressor pRevisionCompressor) throws WikiDragonException {
        NeoBatMediaWikiDumpImporter lNeoBatMediaWikiDumpImporter = new NeoBatMediaWikiDumpImporter(this, pRevisionCompressor);
        lNeoBatMediaWikiDumpImporter.importMediaWikiDump(pInputStream, pCharset);
        return lNeoBatMediaWikiDumpImporter.getMediaWiki();
    }

    @Override
    protected boolean isIndexedGlobal(String pProperty) {
        switch (pProperty) {
            case ATTR_WIKIOBJECT_TYPE: return true;
            default: return false;
        }
    }

    @Override
    protected boolean isIndexedMediaWiki(String pProperty) {
        return false;
    }

    protected static NeoBatMediaWikiCollection create(NeoBatWikiDragonDatabase pNeoBatWikiDragonDatabase) throws WikiDragonException {
        NeoBatMediaWikiCollection lResult = null;
        // Check if it already exists
        if (pNeoBatWikiDragonDatabase.getMediaWikiCollection() != null) throw new WikiDragonException("MediaWikiCollection already exists");
        long lNode = pNeoBatWikiDragonDatabase.database.createNode(new HashMap<>());
        lResult = (NeoBatMediaWikiCollection)pNeoBatWikiDragonDatabase.wikiObjectFactory.getWikiObject(lNode, NeoBatWikiDragonDatabase.NodeType.MEDIAWIKI_COLLECTION);
        lResult.disableAutosaveOnce();
        lResult.setProperty(ATTR_WIKIOBJECT_TYPE, NeoBatWikiDragonDatabase.NodeType.MEDIAWIKI_COLLECTION.name());
        lResult.saveProperties();
        return lResult;
    }
}
