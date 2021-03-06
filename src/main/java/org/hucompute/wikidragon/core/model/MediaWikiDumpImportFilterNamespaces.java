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

package org.hucompute.wikidragon.core.model;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

/**
 * @author Rüdiger Gleim
 */
public class MediaWikiDumpImportFilterNamespaces implements MediaWikiDumpImportFilter {

    protected TIntSet namespaceIDs;

    public MediaWikiDumpImportFilterNamespaces() {
        namespaceIDs = null;
    }

    public MediaWikiDumpImportFilterNamespaces(int... pNamespaceIDs) {
        if (pNamespaceIDs.length == 0) {
            namespaceIDs = null;
        }
        else {
            namespaceIDs = new TIntHashSet(pNamespaceIDs);
        }
    }

    @Override
    public boolean acceptPage(long pId, int pNamespaceId, String pTitle) {
        return (namespaceIDs == null) || (namespaceIDs.contains(pNamespaceId));
    }

    @Override
    public boolean acceptRevision(long pId, int pNamespaceId, String pTitle) {
        return (namespaceIDs == null) || (namespaceIDs.contains(pNamespaceId));
    }
}
