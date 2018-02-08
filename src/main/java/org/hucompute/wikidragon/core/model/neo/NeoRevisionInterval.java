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
import org.hucompute.wikidragon.core.model.Revision;
import org.hucompute.wikidragon.core.model.RevisionInterval;

/**
 * @author Rüdiger Gleim
 */
public class NeoRevisionInterval implements RevisionInterval {

    protected String startRevisionUniqueId;
    protected String endRevisionUniqueId;
    protected Revision startRevision;
    protected Revision endRevision;
    protected NeoWikiDragonDatabase wikiDragonDatabase;

    protected NeoRevisionInterval(NeoWikiDragonDatabase pWikiDragonDatabase, String pStartRevisionID, String pEndRevisionID) {
        wikiDragonDatabase = pWikiDragonDatabase;
        startRevisionUniqueId = pStartRevisionID;
        endRevisionUniqueId = pEndRevisionID;
    }

    @Override
    public Revision getStartRevision() throws WikiDragonException {
        if ((startRevision == null) && (startRevisionUniqueId != null)) {
            startRevision = (Revision)wikiDragonDatabase.getWikiObjectFactory().getWikiObject(startRevisionUniqueId);
        }
        return startRevision;
    }

    @Override
    public Revision getEndRevision() throws WikiDragonException {
        if ((endRevision == null) && (endRevisionUniqueId != null)) {
            endRevision = (Revision)wikiDragonDatabase.getWikiObjectFactory().getWikiObject(endRevisionUniqueId);
        }
        return endRevision;
    }

    @Override
    public String getStartRevisionUniqueId() {
        return startRevisionUniqueId;
    }

    @Override
    public String getEndRevisionUniqueId() {
        return endRevisionUniqueId;
    }
}
