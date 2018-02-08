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

import org.hucompute.wikidragon.core.exceptions.WikiDragonException;

/**
 * @author Rüdiger Gleim
 */
public interface WikiDragonDatabase extends AutoCloseable {

    public void reset() throws WikiDragonException;

    public MediaWikiCollection getMediaWikiCollection();

    public WikiObjectFactory getWikiObjectFactory();

    public WikiTransaction beginTx();

    public void close();

    public IOManager getIOManager();

    public Iterable<WikiDataEntity> getWikiDataEntities();

    public WikiObjectIterator<WikiDataEntity> getWikiDataEntityIterator();

    public WikiDataEntity createWikiDataEntity(String pEntityID) throws WikiDragonException;

    public WikiDataEntity getWikiDataEntity(String pEntityID) throws WikiDragonException;


}
