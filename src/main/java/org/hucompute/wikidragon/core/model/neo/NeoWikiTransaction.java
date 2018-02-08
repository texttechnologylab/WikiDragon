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

import org.hucompute.wikidragon.core.model.WikiTransaction;
import org.neo4j.graphdb.Transaction;

/**
 * @author Rüdiger Gleim
 */
public class NeoWikiTransaction implements WikiTransaction {

    protected NeoWikiDragonDatabase wikiDragonDatabase;
    protected Transaction tx;

    protected NeoWikiTransaction(NeoWikiDragonDatabase pNeoWikiDragonDatabase) {
        wikiDragonDatabase = pNeoWikiDragonDatabase;
        tx = pNeoWikiDragonDatabase.database.beginTx();
    }

    @Override
    public void success() {
        tx.success();
    }

    @Override
    public void failure() {
        tx.failure();
    }

    @Override
    public void terminate() {
        tx.terminate();
    }

    @Override
    public void close() {
        tx.close();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NeoWikiTransaction)) return false;
        return tx.equals(((NeoWikiTransaction)o).tx);
    }

    @Override
    public int hashCode() {
        return tx.hashCode();
    }
}
