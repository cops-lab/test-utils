/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.c0ps.test;

import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.http.client.utils.URIBuilder;

public class HttpTestServer {

    private static final Response DEFAULT_RESPONSE = new Response();
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    private final ServerSocket server;

    private Socket socket;

    private final LinkedList<Response> responses = new LinkedList<>();
    public final List<Request> requests = new LinkedList<>();

    public HttpTestServer(int port) {
        try {
            server = new ServerSocket(port);
            responses.add(DEFAULT_RESPONSE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response addResponse(String mimeType, String body) {
        return addResponse(new Response(mimeType, body));
    }

    public Response addResponse(int statusCode, String mimeType, String body) {
        return addResponse(new Response(statusCode, mimeType, body));
    }

    private Response addResponse(Response r) {
        if (responses.size() == 1 && responses.getFirst() == DEFAULT_RESPONSE) {
            responses.clear();
        }
        responses.add(r);
        return r;
    }

    public void reset() {
        requests.clear();
        responses.clear();
        responses.add(DEFAULT_RESPONSE);
    }

    public void start() {
        EXEC.submit(() -> {
            try {
                nestedStartInThread();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void nestedStartInThread() throws IOException {
        while (!EXEC.isShutdown()) {
            socket = server.accept();

            BufferedReader in = null;
            PrintWriter out = null;

            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream());
                var req = readRequest(in);
                if (req != null) {
                    requests.add(req);
                    var r = responses.size() > 1 //
                            ? responses.pop() //
                            : responses.getFirst();
                    printResponse(r, out);
                }
            } finally {
                close(in, out, socket);
            }
        }
    }

    public void stop() {
        try {
            EXEC.shutdownNow();
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Request readRequest(BufferedReader in) throws IOException {
        var req = new Request();

        var l = in.readLine();
        if (l == null) {
            return null;
        }

        var parts = l.split(" ");
        req.method = parts[0];

        try {
            var x = new URIBuilder("http://host" + parts[1]);
            req.path = x.getPath();
            x.getQueryParams().forEach(p -> {
                req.queryParams.put(p.getName(), p.getValue());
            });
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        while ((l = in.readLine()) != null && !l.trim().isEmpty()) {
            var idx = l.indexOf(':');
            if (idx != -1) {
                var key = l.substring(0, idx).trim();
                var val = l.substring(idx + 1).trim();
                req.headers.put(key, val);
                continue;
            }
        }

        var sb = new StringBuilder();
        for (var i = 0; i < req.getContentLength(); i++) {
            sb.append((char) in.read());
        }

        req.body = sb.toString();
        return req;
    }

    private void printResponse(Response response, PrintWriter out) {
        out.printf("HTTP/1.1 %d\n", response.statusCode);
        out.printf("Server: %s\n", HttpTestServer.class.getSimpleName());
        out.printf("Date: %s\n", new Date());
        out.printf("Content-type: %s\n", response.mimeType);
        out.printf("Content-length: %d\n", response.body.length());

        for (var key : response.headers.keySet()) {
            var val = response.headers.get(key);
            out.printf("%s: %s\n", key, val);
        }

        out.printf("\n"); // empty line separates headers from content
        out.print(response.body); // no new line!
        out.flush();
    }

    private static void close(Closeable... cs) {
        for (Closeable c : cs) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public static class Request {

        private static final String CONTENT_LENGTH = "Content-Length";

        public String method;
        public String path;
        public Map<String, String> queryParams = new LinkedHashMap<>();
        public Map<String, String> headers = new LinkedHashMap<>();
        public String body;

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
        }

        public int getContentLength() {
            if (!headers.containsKey(CONTENT_LENGTH)) {
                return 0;
            }
            return Integer.parseInt(headers.get(CONTENT_LENGTH));
        }
    }

    public static class Response {

        private static final String LAST_MODIFIED = "Last-Modified";
        private static final String LOCATION = "Location";
        private static final SimpleDateFormat GMT = new SimpleDateFormat("EEEE, dd MMM yyyy HH:mm:ss z");

        {
            GMT.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        private int statusCode = 200;
        private String mimeType = "text/plain";
        private String body = "n/a";
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Response() {}

        private Response(String mimeType, String body) {
            this.mimeType = mimeType;
            this.body = body;
        }

        private Response(int statusCode, String mimeType, String body) {
            this.statusCode = statusCode;
            this.mimeType = mimeType;
            this.body = body;
        }

        public Response addHeader(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public Response setLastModified(Date lastModified) {
            return addHeader(LAST_MODIFIED, GMT.format(lastModified));
        }

        public Response setLocation(String location) {
            if (statusCode != 301 && statusCode != 302 && statusCode != 303 && statusCode != 307 && statusCode != 308) {
                throw new IllegalStateException("Setting the location header is only meaningful for status codes 301, 302, 303, 307, and 308");
            }
            return addHeader(LOCATION, location);
        }
    }
}