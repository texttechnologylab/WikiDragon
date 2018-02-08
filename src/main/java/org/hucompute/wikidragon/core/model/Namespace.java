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

import java.util.List;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public interface Namespace extends WikiObject {

    public MediaWiki getMediaWiki();

    public int getId();

    public MediaWikiConst.Case getCase();

    public String getName();

    public String getCanonicalName();

    public Set<String> getAliases();

    public Set<String> getAllNames();

    public Iterable<Page> getPages();

    public List<Page> getPagesList();

    public WikiObjectIterator<Page> getPageIterator();

    public Page getPage(String pTitle);

}