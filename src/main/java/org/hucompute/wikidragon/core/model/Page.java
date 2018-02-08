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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public interface Page extends WikiObject {

    public String getTitle();

    public String getQualifiedTitle() throws WikiDragonException;

    public int getNamespaceID();

    public long getId();

    public Iterable<Revision> getRevisions();

    public WikiObjectIterator<Revision> getRevisionsIterator();

    /**
     * Get Revision which has been valid at the specific timestamp
     * @param pTimestamp
     * @return Revision which has been valid at the specific timestamp
     */
    public Revision getRevisionAt(ZonedDateTime pTimestamp);

    public List<Revision> getRevisionsList();

    public MediaWiki getMediaWiki();

    /**
     * Get PageTier of a specific timestamp (exact match)
     * @param pTimestamp
     * @return PageTier of a specific timestamp (exact match)
     */
    public PageTier getPageTierAt(ZonedDateTime pTimestamp);

    public List<PageTier> getPageTierList();

    public int getRevisionCount();

    public Revision getFirstRevision();

    public Revision getLatestRevision();

    public PageTier createPageTier(ZonedDateTime pTimestamp) throws WikiDragonException;

    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, Contributor pContributor, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException;

    public Revision createRevision(long pRevisionID, long pParentID, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksIn() throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksIn(ZonedDateTime pTimestamp) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksOut() throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksOut(ZonedDateTime pTimestamp) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksIn(MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksIn(ZonedDateTime pTimestamp, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksOut(MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException;

    public Set<WikiPageLink> getWikiPageLinksOut(ZonedDateTime pTimestamp, MediaWikiConst.LinkType... pLinkTypes) throws WikiDragonException;

    public Map<String, List<WikiDataEntity>> getAspectWikiDataEntityMap() throws WikiDragonException;

    public void createAspectWikiDataEntityRelation(WikiDataEntity pWikiDataEntity, String pAspect) throws WikiDragonException;

    public void addWikiPageLinksOut(ZonedDateTime pTimestamp, Map<MediaWikiConst.LinkType, Set<Page>> pTypePagesMap) throws WikiDragonException;

}

