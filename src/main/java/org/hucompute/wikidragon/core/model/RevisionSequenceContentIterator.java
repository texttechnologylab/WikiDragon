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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.hucompute.wikidragon.core.exceptions.WikiDragonException;
import org.hucompute.wikidragon.core.util.IOUtil;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Rüdiger Gleim
 */
public class RevisionSequenceContentIterator implements Iterator<RevisionContentTuple>, AutoCloseable {

    private static Logger logger = LogManager.getLogger(RevisionSequenceContentIterator.class);

    public static enum RevisionContentType{RAWTEXT, HTML};

    protected WikiDragonDatabase wikiDragonDatabase;
    protected RevisionContentType revisionContentType;
    protected WikiTransaction tx;
    protected List<Revision> revisionList;
    protected int index;
    protected String currentContent;

    public RevisionSequenceContentIterator(Revision pStartRevision, RevisionContentType pRevisionContentType) {
        wikiDragonDatabase = pStartRevision.getWikiDragonDatabase();
        revisionContentType = pRevisionContentType;
        tx = wikiDragonDatabase.beginTx();
        revisionList = pStartRevision.getPage().getRevisionsList();
        index = revisionList.indexOf(pStartRevision);
        if (hasNext()) {
            try {
                switch (revisionContentType) {
                    case RAWTEXT: {
                        currentContent = revisionList.get(index).getRawText();
                        break;
                    }
                    case HTML: {
                        currentContent = revisionList.get(index).getHtml();
                        break;
                    }
                    default: {
                        currentContent = null;
                        break;
                    }
                }
            }
            catch (WikiDragonException e) {
                logger.error(e.getMessage(), e);
                currentContent = null;
                close();
            }
        }
    }

    @Override
    public boolean hasNext() {
        return (index>=0) && (index < revisionList.size());
    }

    @Override
    public RevisionContentTuple next() {
        RevisionContentTuple lResult = null;
        if (currentContent == null) {
            close();
        }
        if (!hasNext()) {
            close();
        }
        else {
            lResult = new RevisionContentTuple(revisionList.get(index), currentContent);
            index++;
            if (index < revisionList.size()) {
                try {
                    Revision lNextRevision = revisionList.get(index);
                    WikiDragonConst.Compression lCompression = null;
                    byte[] lCompressed = null;
                    switch (revisionContentType) {
                        case RAWTEXT: {
                            lCompression = lNextRevision.getRawTextCompression();
                            lCompressed = lNextRevision.getCompressedRawText();
                            break;
                        }
                        case HTML: {
                            lCompression = lNextRevision.getHtmlCompression();
                            lCompressed = lNextRevision.getCompressedRawHtml();
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                    if (lCompressed != null) {
                        switch (lCompression) {
                            case NONE:
                            case LZMA2:
                            case GZIP:
                            case BZIP2: {
                                currentContent = IOUtil.uncompress(lCompressed, lCompression);
                                break;
                            }
                            case DIFFBZIP2: {
                                String lUncompressed = IOUtil.uncompress(lCompressed, WikiDragonConst.Compression.BZIP2);
                                DiffMatchPatch lDiff = new DiffMatchPatch();
                                currentContent = (String) lDiff.patchApply((LinkedList<DiffMatchPatch.Patch>)(lDiff.patchFromText(lUncompressed)), currentContent)[0];
                                break;
                            }
                        }
                    } else {
                        close();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    close();
                    return null;
                }
            }
        }
        return lResult;
    }

    @Override
    public void close() {
        index = -1;
        if (tx != null) {
            tx.success();
            tx.close();
        }
        tx = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (tx != null) {
            tx.success();
            tx.close();
        }
    }

}
