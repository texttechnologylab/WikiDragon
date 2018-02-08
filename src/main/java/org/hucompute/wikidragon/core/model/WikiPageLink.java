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

import org.hucompute.wikidragon.core.util.StringUtil;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * @author Rüdiger Gleim
 */
public class WikiPageLink {

    public static enum WikiPageLinkSource {HtmlParsedAdHoc, HtmlParsedDB, SQLDump};

    protected MediaWikiConst.LinkType linkType;
    protected Page source;
    protected Page target;
    protected WikiPageLinkSource wikiPageLinkSource;
    protected ZonedDateTime timestamp;
    private String timestampUTCString;

    public WikiPageLink(MediaWikiConst.LinkType linkType, Page source, Page target, WikiPageLinkSource wikiPageLinkSource, ZonedDateTime pTimestamp) {
        this.linkType = linkType;
        this.source = source;
        this.target = target;
        this.wikiPageLinkSource = wikiPageLinkSource;
        this.timestamp = pTimestamp == null ? WikiDragonConst.NULLDATETIME : pTimestamp;
        timestampUTCString = StringUtil.zonedDateTime2String(this.timestamp.withZoneSameInstant(ZoneId.of("UTC")));
    }

    public MediaWikiConst.LinkType getLinkType() {
        return linkType;
    }

    public Page getSource() {
        return source;
    }

    public Page getTarget() {
        return target;
    }

    public WikiPageLinkSource getWikiPageLinkSource() {
        return wikiPageLinkSource;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public boolean equals(Object o) {
        if (!(o instanceof WikiPageLink)) {
            return false;
        }
        WikiPageLink lOther = (WikiPageLink)o;
        return timestampUTCString.equals(lOther.timestampUTCString) && linkType.equals(lOther.linkType) && (source.getId() == lOther.source.getId()) && (target.getId() == lOther.target.getId());
    }

    public int hashCode() {
        return (timestampUTCString+"\t"+linkType.name()+"\t"+source.getId()+"\t"+target.getId()).hashCode();
    }
}
