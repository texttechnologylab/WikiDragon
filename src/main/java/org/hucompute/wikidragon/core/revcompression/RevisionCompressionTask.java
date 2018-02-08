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

package org.hucompute.wikidragon.core.revcompression;

import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.WikiDragonConst;

import java.time.ZonedDateTime;

/**
 * @author Rüdiger Gleim
 */
public class RevisionCompressionTask {

    protected Page page;
    protected long revisionID;
    protected long parentID;
    protected ZonedDateTime timestamp;
    protected String userName;
    protected long userID;
    protected String ip;
    protected String comment;
    protected boolean minor;
    protected MediaWikiConst.Model model;
    protected MediaWikiConst.Format format;
    protected String sha1;
    protected String rawText;
    protected byte[] compressedText;
    protected int bytes;
    protected WikiDragonConst.Compression compression;

    public RevisionCompressionTask(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) {
        page = pPage;
        revisionID = pRevisionID;
        parentID = pParentId;
        timestamp = pTimestamp;
        userName = pUserName;
        userID = pUserID;
        ip = null;
        comment = pComment;
        minor = pMinor;
        model = pModel;
        format = pFormat;
        sha1 = pSHA1;
        rawText = pRawText;
        bytes = pBytes;
    }

    public RevisionCompressionTask(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) {
        page = pPage;
        revisionID = pRevisionID;
        parentID = pParentId;
        timestamp = pTimestamp;
        userName = null;
        userID = WikiDragonConst.NULLNODEID;
        ip = pIP;
        comment = pComment;
        minor = pMinor;
        model = pModel;
        format = pFormat;
        sha1 = pSHA1;
        rawText = pRawText;
        bytes = pBytes;
    }

    public int getBytes() {
        return bytes;
    }

    public void setCompressedText(byte[] compressedText) {
        this.compressedText = compressedText;
    }

    public void setCompression(WikiDragonConst.Compression compression) {
        this.compression = compression;
    }

    public Page getPage() {
        return page;
    }

    public long getRevisionID() {
        return revisionID;
    }

    public long getParentID() {
        return parentID;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getUserName() {
        return userName;
    }

    public long getUserID() {
        return userID;
    }

    public String getIp() {
        return ip;
    }

    public String getComment() {
        return comment;
    }

    public boolean isMinor() {
        return minor;
    }

    public MediaWikiConst.Model getModel() {
        return model;
    }

    public MediaWikiConst.Format getFormat() {
        return format;
    }

    public String getSha1() {
        return sha1;
    }

    public String getRawText() {
        return rawText;
    }

    public byte[] getCompressedText() {
        return compressedText;
    }

    public WikiDragonConst.Compression getCompression() {
        return compression;
    }
}
