package com.xiddoc.androidautox.store;

import java.io.IOException;

/** Downloads over HTTPS. Implementations must refuse anything that is not https. */
public interface HttpFetcher {

    /**
     * @param accept value for the {@code Accept} header
     * @return an open response with a 2xx status; the caller closes it
     * @throws IOException on network errors, non-2xx statuses, or a redirect off https
     */
    HttpResponse get(String url, String accept) throws IOException;
}
