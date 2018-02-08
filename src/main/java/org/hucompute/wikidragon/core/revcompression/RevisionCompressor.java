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

import org.hucompute.wikidragon.core.events.RevisionCompressionListener;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.model.WikiDragonConst;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Rüdiger Gleim
 */
public abstract class RevisionCompressor {

    protected Set<RevisionCompressionListener> listeners;

    public RevisionCompressor() {
        listeners = new HashSet<>();
    }

    public void addRevisionCompressionListener(RevisionCompressionListener pRevisionCompressionListener) {
        listeners.add(pRevisionCompressionListener);
    }

    public void removeRevisionCompressionListener(RevisionCompressionListener pRevisionCompressionListener) {
        listeners.remove(pRevisionCompressionListener);
    }

    public void fireRevisionCompressed(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        for (RevisionCompressionListener l:listeners) {
            l.revisionCompressed(pPage, pRevisionID, pParentId, pTimestamp, pUserName, pUserID, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pCompressedText, pCompression, pBytes);
        }
    }

    public void fireRevisionCompressed(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, byte[] pCompressedText, WikiDragonConst.Compression pCompression, int pBytes) throws WikiDragonException {
        for (RevisionCompressionListener l:listeners) {
            l.revisionCompressed(pPage, pRevisionID, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pCompressedText, pCompression, pBytes);
        }
    }

    public abstract void submitRevision(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException;

    public abstract void submitRevision(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, int pBytes) throws WikiDragonException;

    /**
     * Call method to ensure that any buffers are closed and pending revcompression tasks are finished
     */
    public abstract void close() throws InterruptedException, WikiDragonException;

}
