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

package org.hucompute.wikidragon.core.revcompression.diff;

import org.hucompute.wikidragon.core.model.MediaWikiConst;
import org.hucompute.wikidragon.core.model.Page;
import org.hucompute.wikidragon.core.revcompression.RevisionCompressionTask;

import java.time.ZonedDateTime;

/**
 * @author Rüdiger Gleim
 */
public class RevisionDiffCompressionTask extends RevisionCompressionTask {

    protected boolean forceKeyFrame;

    public RevisionDiffCompressionTask(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pUserName, long pUserID, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, boolean pForceKeyFrame, int pBytes) {
        super(pPage, pRevisionID, pParentId, pTimestamp, pUserName, pUserID, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
        forceKeyFrame = pForceKeyFrame;
    }

    public RevisionDiffCompressionTask(Page pPage, long pRevisionID, long pParentId, ZonedDateTime pTimestamp, String pIP, String pComment, boolean pMinor, MediaWikiConst.Model pModel, MediaWikiConst.Format pFormat, String pSHA1, String pRawText, boolean pForceKeyFrame, int pBytes) {
        super(pPage, pRevisionID, pParentId, pTimestamp, pIP, pComment, pMinor, pModel, pFormat, pSHA1, pRawText, pBytes);
        forceKeyFrame = pForceKeyFrame;
    }

    public boolean isForceKeyFrame() {
        return forceKeyFrame;
    }

    public void setForceKeyFrame(boolean forceKeyFrame) {
        this.forceKeyFrame = forceKeyFrame;
    }
}
