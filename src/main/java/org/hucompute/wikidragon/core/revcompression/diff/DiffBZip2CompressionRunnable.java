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

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.hucompute.wikidragon.core.model.WikiDragonConst;

import java.nio.charset.Charset;
import java.util.LinkedList;

/**
 * @author Rüdiger Gleim
 */
public class DiffBZip2CompressionRunnable implements Runnable {

    private static Logger logger = LogManager.getLogger(DiffBZip2CompressionRunnable.class);

    protected DiffRevisionCompressor diffRevisionCompressor;
    protected RevisionDiffCompressionTask revisionCompressionTask;
    protected Exception exception;

    public DiffBZip2CompressionRunnable(DiffRevisionCompressor pDiffRevisionCompressor, RevisionDiffCompressionTask pRevisionCompressionTask) {
        diffRevisionCompressor = pDiffRevisionCompressor;
        revisionCompressionTask = pRevisionCompressionTask;
    }

    public RevisionDiffCompressionTask getRevisionCompressionTask() {
        return revisionCompressionTask;
    }

    public Exception getException() {
        return exception;
    }

    public void run() {
        exception = null;
        try {
            DiffMatchPatch lDiff = new DiffMatchPatch();
            String lSource = diffRevisionCompressor.getRawText(revisionCompressionTask.getParentID());
            if (!revisionCompressionTask.isForceKeyFrame() && (lSource != null) && (lSource.length() > 0)) {
                String lTarget = revisionCompressionTask.getRawText();
                LinkedList<DiffMatchPatch.Diff> lDiffs = lDiff.diffMain(lSource, lTarget);
                lDiff.diffCleanupEfficiency(lDiffs);
                LinkedList<DiffMatchPatch.Patch> lPatches = lDiff.patchMake(lSource, lDiffs);
                ByteArrayOutputStream lByteArrayOutputStream = new ByteArrayOutputStream();
                BZip2CompressorOutputStream lBZip2CompressorOutputStream = new BZip2CompressorOutputStream(lByteArrayOutputStream);
                byte[] lData = lDiff.patchToText(lPatches).getBytes(Charset.forName("UTF-8"));
                lBZip2CompressorOutputStream.write(lData);
                lBZip2CompressorOutputStream.flush();
                lBZip2CompressorOutputStream.close();
                revisionCompressionTask.setCompressedText(lByteArrayOutputStream.toByteArray());
                revisionCompressionTask.setCompression(WikiDragonConst.Compression.DIFFBZIP2);
            }
            else {
                String lTarget = revisionCompressionTask.getRawText();
                ByteArrayOutputStream lByteArrayOutputStream = new ByteArrayOutputStream();
                BZip2CompressorOutputStream lBZip2CompressorOutputStream = new BZip2CompressorOutputStream(lByteArrayOutputStream);
                byte[] lData = lTarget.getBytes(Charset.forName("UTF-8"));
                lBZip2CompressorOutputStream.write(lData);
                lBZip2CompressorOutputStream.flush();
                lBZip2CompressorOutputStream.close();
                revisionCompressionTask.setCompressedText(lByteArrayOutputStream.toByteArray());
                revisionCompressionTask.setCompression(WikiDragonConst.Compression.BZIP2);
            }
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
        try {
            diffRevisionCompressor.finishDiffBZip2CompressionRunnable(this);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            exception = e;
        }
    }

}
