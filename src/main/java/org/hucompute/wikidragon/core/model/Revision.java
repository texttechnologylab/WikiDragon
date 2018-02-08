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
public interface Revision extends WikiObject {

    public long getId();

    public ZonedDateTime getTimestamp();

    public Contributor getContributor();

    public String getComment();

    public String getIp();

    /**
     * Get Either IP or name of Contributor
     */
    public String getAuthor();

    public MediaWikiConst.Model getModel();

    public MediaWikiConst.Format getFormat();

    public String getSHA1();

    public byte[] getCompressedRawText();

    public WikiDragonConst.Compression getRawTextCompression();

    public Revision getParentRevision();

    public String getRawText() throws WikiDragonException;

    public Page getPage();

    /**
     * Get WikiPageOutLinks ad hoc from HTML
     * @return WikiPageOutLinks fetched ad hoc from HTML
     * @throws WikiDragonException
     */
    public Set<WikiPageLink> getWikiPageOutLinks() throws WikiDragonException;

    /**
     * Get WikiPageOutLinks ad hoc from HTML
     * @param pAcceptLinkTypes
     * @return WikiPageOutLinks fetched ad hoc from HTML
     * @throws WikiDragonException
     */
    public Set<WikiPageLink> getWikiPageOutLinks(MediaWikiConst.LinkType... pAcceptLinkTypes) throws WikiDragonException;

    /**
     * Get HTML ad hoc or from the database (if available)
     * @return
     */
    public String getHtml() throws WikiDragonException;

    public String getPlainText() throws WikiDragonException;

    public boolean isMinor() throws WikiDragonException;

    public void setCompressedRawHtml(byte[] pData);

    public byte[] getCompressedRawHtml();

    public String getTEI() throws WikiDragonException;

    public WikiDragonConst.Compression getHtmlCompression();

    public void setHtmlCompression(WikiDragonConst.Compression pCompression);

    public Iterable<RevisionContentTuple> getRevisionContentTuples(RevisionSequenceContentIterator.RevisionContentType pRevisionContentType);

    public RevisionSequenceContentIterator getRevisionContentTupleIterator(RevisionSequenceContentIterator.RevisionContentType pRevisionContentType);

    /**
     * Get Text-Size in Bytes - This is parsed from Stub-Meta History, or fetched from the Raw-Text if available.
     * Returns -1 if not information is available at all
     * @return
     * @throws WikiDragonException
     */
    public int getBytes() throws WikiDragonException;;

}
