/*
 * Copyright 2022 Delft University of Technology
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

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.glassfish.jersey.client.ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class HttpTestServerTest {

    private static final int PORT = 1234;

    private static HttpTestServer sut;

    @BeforeAll
    public static void setupAll() {
        sut = new HttpTestServer(PORT);
        sut.start();
    }

    @BeforeEach
    public void setup() {
        sut.reset();
    }

    private static Builder baseRequest(String path, String mediaType, String... queryParam) {
        var cfg = new ClientConfig();
        cfg.property(SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true);

        var target = ClientBuilder.newClient(cfg) //
                .target("http://127.0.0.1:" + PORT + "/") //
                .path(path);

        // query params are expected to be formatted like "a:1"
        for (String q : queryParam) {
            var parts = q.split(":");
            target = target.queryParam(parts[0], parts[1]);
        }

        return target //
                .request(mediaType) //
                .accept(mediaType);
    }

    @AfterAll
    public static void teardownAll() {
        sut.stop();
    }

    @Test
    public void servesRequestsWithDefaultHandler() {
        var r = baseRequest("/", TEXT_PLAIN).get(Response.class);
        var body = r.readEntity(String.class);

        assertEquals(200, r.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, r.getMediaType());
        assertEquals("n/a", body);
    }

    @Test
    public void configCanBeReset() {
        // run any other test
        requestHandlerCanBeChanged();
        sut.reset();
        // and yet another one
        servesRequestsWithDefaultHandler();
        assertEquals(1, sut.requests.size());
    }

    @Test
    public void requestHandlerCanBeChanged() {
        sut.addResponse(APPLICATION_JSON, "[1,2,3]");

        var r = baseRequest("/", APPLICATION_JSON).get(Response.class);
        var body = r.readEntity(String.class);

        assertEquals(200, r.getStatus());
        assertEquals(APPLICATION_JSON_TYPE, r.getMediaType());
        assertEquals("[1,2,3]", body);
    }

    @Test
    public void statusCodeCanBeChanged() {
        sut.addResponse(404, APPLICATION_JSON, "[1,2,3]");

        var r = baseRequest("/", APPLICATION_JSON).get(Response.class);
        var body = r.readEntity(String.class);

        assertEquals(404, r.getStatus());
        assertEquals(APPLICATION_JSON_TYPE, r.getMediaType());
        assertEquals("[1,2,3]", body);
    }

    @Test
    public void requestAreBeingLogged_method() {
        baseRequest("/get", TEXT_PLAIN).get();
        baseRequest("/delete", TEXT_PLAIN).delete();

        var actual = sut.requests.stream().map(r -> r.method).collect(Collectors.toList());
        var expected = List.of("GET", "DELETE");
        assertEquals(expected, actual);
    }

    @Test
    public void requestAreBeingLogged_path() {
        baseRequest("/", TEXT_PLAIN).get();
        baseRequest("/a", TEXT_PLAIN).get();
        baseRequest("/a/", TEXT_PLAIN).get();
        baseRequest("/b/c", TEXT_PLAIN).get();

        var actual = sut.requests.stream().map(r -> r.path).collect(Collectors.toList());
        var expected = List.of("/", "/a", "/a/", "/b/c");
        assertEquals(expected, actual);
    }

    @Test
    public void requestAreBeingLogged_noQueryParams() {
        baseRequest("/a", TEXT_PLAIN).get();

        assertEquals(1, sut.requests.size());
        var actual = sut.requests.get(0).queryParams;
        var expected = Map.of();
        assertEquals(expected, actual);
    }

    @Test
    public void requestAreBeingLogged_oneQueryParam() {
        baseRequest("/a", TEXT_PLAIN, "a:1").get();

        assertEquals(1, sut.requests.size());
        var actual = sut.requests.get(0).queryParams;
        var expected = Map.of("a", "1");
        assertEquals(expected, actual);
    }

    @Test
    public void requestAreBeingLogged_twoQueryParams() {
        baseRequest("/a", TEXT_PLAIN, "a:1", "b:2").get();

        assertEquals(1, sut.requests.size());
        var actual = sut.requests.get(0).queryParams;
        var expected = Map.of("a", "1", "b", "2");
        assertEquals(expected, actual);
    }

    @Test
    public void requestAreBeingLogged_headers() {
        baseRequest("/", TEXT_PLAIN).header("a", "b").get();
        assertEquals(1, sut.requests.size());
        var actual = sut.requests.get(0).headers;
        assertTrue(actual.containsKey("a"));
        assertEquals(actual.get("a"), "b");
    }

    @Test
    public void requestAreBeingLogged_body() {
        var e = Entity.entity("xxx", MediaType.TEXT_PLAIN);
        baseRequest("/", TEXT_PLAIN).post(e);

        assertEquals(1, sut.requests.size());
        var actual = sut.requests.get(0).body;
        var expected = "xxx";
        assertEquals(expected, actual);
    }

    @Test
    public void responseHasDefault() {
        var actual = someRequest().readEntity(String.class);
        assertEquals("n/a", actual);
    }

    @Test
    public void responseCanBeSet() {
        sut.addResponse(TEXT_PLAIN, "x1");
        var actual = someRequest().readEntity(String.class);
        assertEquals("x1", actual);
    }

    @Test
    public void responseIsReset() {
        sut.addResponse(TEXT_PLAIN, "x2");
        sut.reset();
        var actual = someRequest().readEntity(String.class);
        assertEquals("n/a", actual);
    }

    @Test
    public void responseDefaultIsReplacedWithAdd() {
        sut.addResponse(TEXT_PLAIN, "x3");
        var actual = someRequest().readEntity(String.class);
        assertEquals("x3", actual);
    }

    @Test
    public void responseCanBeVaried() {
        sut.addResponse(TEXT_PLAIN, "x4");
        sut.addResponse(TEXT_PLAIN, "x5");

        var r1 = someRequest().readEntity(String.class);
        var r2 = someRequest().readEntity(String.class);

        assertEquals("x4", r1);
        assertEquals("x5", r2);
    }

    @Test
    public void lastResponseIsRepeated() {
        sut.addResponse(TEXT_PLAIN, "x6");
        sut.addResponse(TEXT_PLAIN, "x7");

        someRequest();
        someRequest();
        var actual = someRequest().readEntity(String.class);

        assertEquals("x7", actual);
    }

    @Test
    public void lastModifiedIsNotSetByDefault() {
        assertNull(someRequest().getLastModified());
    }

    @Test
    public void lastModifiedCanBeSet() {
        var d = new Date(123 * 1000);
        sut.addResponse(TEXT_PLAIN, "...").setLastModified(d);

        var actual = someRequest().getLastModified();

        var expected = new Date(123 * 1000).getTime();
        assertNotNull(actual);
        assertTrue(expected - actual.getTime() < 1000);
    }

    @Test
    public void locationIsNotSetByDefault() {
        assertNull(someRequest().getLocation());
    }

    @Test
    public void locationCanBeSet() throws URISyntaxException {
        sut.addResponse(301, TEXT_PLAIN, "...").setLocation("https://somewhere");

        Response r = someRequest();
        var actual = r.getLocation();
        var expected = new URI("https://somewhere");
        assertEquals(expected, actual);
    }

    @Test
    public void locationChecksStatusCode() throws URISyntaxException {
        var someUrl = "https://somewhere";
        sut.addResponse(301, TEXT_PLAIN, "...").setLocation(someUrl);
        sut.addResponse(302, TEXT_PLAIN, "...").setLocation(someUrl);
        sut.addResponse(303, TEXT_PLAIN, "...").setLocation(someUrl);
        sut.addResponse(307, TEXT_PLAIN, "...").setLocation(someUrl);
        sut.addResponse(308, TEXT_PLAIN, "...").setLocation(someUrl);

        var e = assertThrows(IllegalStateException.class, () -> {
            sut.addResponse(200, TEXT_PLAIN, "...").setLocation(someUrl);
        });
        assertEquals("Setting the location header is only meaningful for status codes 301, 302, 303, 307, and 308", e.getMessage());
    }

    private Response someRequest() {
        return baseRequest("/", TEXT_PLAIN).get(Response.class);
    }

    public Response get(String path, String mediaType) {
        var base = baseRequest(path, mediaType);
        return base.get(Response.class);
    }
}