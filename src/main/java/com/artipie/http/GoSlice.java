/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.http;

import com.artipie.asto.Storage;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rt.RtRule;
import com.artipie.http.rt.SliceRoute;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.http.slice.SliceDownload;
import com.artipie.http.slice.SliceSimple;
import com.artipie.http.slice.SliceWithHeaders;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.regex.Pattern;
import org.reactivestreams.Publisher;

/**
 * Slice implementation that provides HTTP API (Go module proxy protocol) for Golang repository.
 * @since 0.3
 * @todo #31:30min Create test (not ITcase) for this class: we need to properly test routing and
 *  fallback if no routes found.
 * @todo #31:30min Create class with main method to start go-adapter and describe setup/startup
 *  process and use scenarios in readme.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public final class GoSlice implements Slice {

    /**
     * Content-type header name.
     */
    private static final String CONTENT_TYPE = "content-type";

    /**
     * Text header.
     */
    private static final String TEXT_PLAIN = "text/plane";

    /**
     * Origin.
     */
    private final Slice origin;

    /**
     * Ctor.
     * @param storage Storage
     */
    public GoSlice(final Storage storage) {
        this.origin = new SliceRoute(
            GoSlice.pathGet(
                ".+/@v/v.*\\.info",
                new SliceWithHeaders(
                    new SliceDownload(storage),
                    new Headers.From(GoSlice.CONTENT_TYPE, "application/json")
                )
            ),
            GoSlice.pathGet(
                ".+/@v/v.*\\.mod",
                new SliceWithHeaders(
                    new SliceDownload(storage),
                    new Headers.From(GoSlice.CONTENT_TYPE, GoSlice.TEXT_PLAIN)
                )
            ),
            GoSlice.pathGet(
                ".+/@v/v.*\\.zip",
                new SliceWithHeaders(
                    new SliceDownload(storage),
                    new Headers.From(GoSlice.CONTENT_TYPE, "application/zip")
                )
            ),
            GoSlice.pathGet(
                ".+/@v/list",
                new SliceWithHeaders(
                    new SliceDownload(storage),
                    new Headers.From(GoSlice.CONTENT_TYPE, GoSlice.TEXT_PLAIN)
                )
            ),
            GoSlice.pathGet(".+/@latest", new LatestSlice(storage)),
            new SliceRoute.Path(
                RtRule.FALLBACK,
                new SliceSimple(
                    new RsWithStatus(RsStatus.NOT_FOUND)
                )
            )
        );
    }

    @Override
    public Response response(
        final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return this.origin.response(line, headers, body);
    }

    /**
     * This method simply encapsulates all the RtRule instantiations.
     * @param pattern Route pattern
     * @param slice Slice implementation
     * @return Path route slice
     */
    private static SliceRoute.Path pathGet(final String pattern, final Slice slice) {
        return new SliceRoute.Path(
            new RtRule.Multiple(
                new RtRule.ByPath(Pattern.compile(pattern)),
                new RtRule.ByMethod(RqMethod.GET)
            ),
            new LoggingSlice(slice)
        );
    }
}
