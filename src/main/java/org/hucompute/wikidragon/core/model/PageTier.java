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

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public interface PageTier extends WikiObject, Comparable<PageTier> {

    public enum TierAttribute {HTML};

    public Page getPage();

    public ZonedDateTime getTimestamp();

    public void setTierAttribute(TierAttribute pTierAttribute, String pString) throws WikiDragonException;

    public void setTierAttribute(TierAttribute pTierAttribute, String pString, WikiDragonConst.Compression pDefaultCompression) throws WikiDragonException;

    public String getTierAttribute(TierAttribute pTierAttribute) throws WikiDragonException;

    public boolean hasTierAttribute(TierAttribute pTierAttribute);

    public String getHtml() throws WikiDragonException;

    public String getPlainText() throws WikiDragonException;

    public String getTEI() throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksIn() throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksOut() throws WikiDragonException;

}
