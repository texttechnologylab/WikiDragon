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

package org.hucompute.wikidragon.core.parsing;

import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWiki;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.PageTier;
import org.hucompute.wikidragon.core.model.Revision;

import java.time.ZonedDateTime;

/**
 * @author Rüdiger Gleim
 */
public class XOWATierMassParserRunnable implements Runnable {

    protected XOWATierMassParser xowaTierMassParser;
    protected MediaWiki mediaWiki;
    protected XOWAParser xowaParser;
    protected Page page;
    protected Revision revision;
    protected String pageTitle;
    protected PageTier pageTier;
    protected String text;
    protected ZonedDateTime timestamp;
    protected String result;
    protected WikiDragonException exception;

    public XOWATierMassParserRunnable(XOWATierMassParser pXOWATierMassParser, MediaWiki pMediaWiki) throws WikiDragonException {
        xowaTierMassParser = pXOWATierMassParser;
        mediaWiki = pMediaWiki;
        xowaParser = new XOWAParser(mediaWiki, xowaTierMassParser.getTitleTextCacheMap());
    }

    public void setParameters(Page pPage, Revision pRevision, PageTier pPageTier, ZonedDateTime pTimestamp) {
        page = pPage;
        revision = pRevision;
        pageTier = pPageTier;
        timestamp = pTimestamp;
    }

    public MediaWiki getMediaWiki() {
        return mediaWiki;
    }

    public Revision getRevision() {
        return revision;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public String getText() {
        return text;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getResult() {
        return result;
    }

    public WikiDragonException getException() {
        return exception;
    }

    public PageTier getPageTier() {
        return pageTier;
    }

    public Page getPage() {
        return page;
    }

    public void run() {
        exception = null;
        try {
            text = revision.getRawText();
            pageTitle = page.getTitle();
            result = xowaParser.parse(pageTitle, text, timestamp);
        }
        catch (Exception e) {
            if (e instanceof WikiDragonException) {
                exception = (WikiDragonException)e;
            }
            else {
                exception = new WikiDragonException(e);
            }
        }
        xowaTierMassParser.finishTask(this);
    }

}
