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

import org.hucompute.wikidragon.core.model.WikiObject;
import org.hucompute.wikidragon.core.model.WikiObjectIterator;
import org.neo4j.graphdb.index.IndexHits;

/**
 * @author Rüdiger Gleim
 */
public class NeoBatWikiObjectIterator<G extends WikiObject> implements WikiObjectIterator<G>{

    protected enum Mode {INDEXHITS, ARRAY};

    protected IndexHits<Long> indexHits;
    protected NeoBatWikiDragonDatabase wikiDragonDatabase;
    protected NeoBatWikiObjectFactory wikiObjectFactory;
    protected Mode mode;

    protected long[] nodeIds;
    protected int index = -1;

    protected NeoBatWikiObjectIterator(NeoBatWikiDragonDatabase pNeoWikiDragonDatabase, IndexHits<Long> pIndexHits) {
        wikiDragonDatabase = pNeoWikiDragonDatabase;
        indexHits = pIndexHits;
        wikiObjectFactory = (NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory();
        mode = Mode.INDEXHITS;
    }

    protected NeoBatWikiObjectIterator(NeoBatWikiDragonDatabase pNeoWikiDragonDatabase, long[] pNodeIDs) {
        wikiDragonDatabase = pNeoWikiDragonDatabase;
        indexHits = null;
        nodeIds = pNodeIDs;
        wikiObjectFactory = (NeoBatWikiObjectFactory)wikiDragonDatabase.getWikiObjectFactory();
        mode = Mode.ARRAY;
    }

    @Override
    public boolean hasNext() {
        switch (mode) {
            case INDEXHITS: {
                boolean lResult = indexHits.hasNext();
                if (!lResult) close();
                return lResult;
            }
            case ARRAY: {
                return (((index == -1) && (nodeIds.length > 0)) || ((index>=0) && (index <nodeIds.length-1)));
            }
            default: {
                return false;
            }
        }
    }

    @Override
    public G next() {
        switch (mode) {
            case INDEXHITS: {
                Long lNode = indexHits.next();
                if (lNode == null) {
                    close();
                    return null;
                }
                else {
                    return (G)wikiObjectFactory.getWikiObject(lNode);
                }
            }
            case ARRAY: {
                if (hasNext()) {
                    index++;
                    return (G)wikiObjectFactory.getWikiObject(nodeIds[index]);
                }
                else {
                    return null;
                }
            }
            default: {
                return null;
            }
        }
    }

    @Override
    public void close() {
        switch (mode) {
            case INDEXHITS: {
                if (indexHits != null) {
                    indexHits.close();
                    indexHits = null;
                }
                break;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        switch (mode) {
            case INDEXHITS: {
                if (indexHits != null) {
                    indexHits.close();
                    indexHits = null;
                }
                break;
            }
        }
    }
}
