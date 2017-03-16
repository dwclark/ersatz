/*
 * Copyright (C) 2017 Christopher J. Stehno
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stehno.ersatz

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import spock.lang.AutoCleanup
import spock.lang.Specification

import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

import static MultipartResponseContent.multipart
import static com.stehno.ersatz.ContentType.MULTIPART_MIXED
import static com.stehno.ersatz.ContentType.TEXT_PLAIN
import static com.stehno.ersatz.impl.MimeMultipartResponseContent.mimeMultipart
import static org.hamcrest.Matchers.greaterThanOrEqualTo
import static org.hamcrest.Matchers.startsWith

class ErsatzServerSpec extends Specification {

    private final OkHttpClient client = new OkHttpClient.Builder().cookieJar(new InMemoryCookieJar()).build()

    @AutoCleanup('stop') private final ErsatzServer ersatzServer = new ErsatzServer({
        autoStart()
        encoder MULTIPART_MIXED, MultipartResponseContent, Encoders.multipart
    })

    def 'prototype: functional'() {
        setup:
        ersatzServer.expectations({ expectations ->
            expectations.get('/foo').responds().content('This is Ersatz!!')
            expectations.get('/bar').responds().content('This is Bar!!')
        } as Consumer<Expectations>)

        ersatzServer.start()

        when:
        String text = "http://localhost:${ersatzServer.httpPort}/foo".toURL().text

        then:
        text == 'This is Ersatz!!'
    }

    def 'prototype: groovy'() {
        setup:
        final AtomicInteger counter = new AtomicInteger()

        ersatzServer.expectations {
            get('/foo').called(greaterThanOrEqualTo(1)).responder {
                content 'This is Ersatz!!'
            }.responder {
                content 'This is another response'
            }

            get('/bar') {
                called greaterThanOrEqualTo(2)
                listener { req -> counter.incrementAndGet() }
                responder {
                    content 'This is Bar!!'
                }
            }

            get('/baz').query('alpha', '42').responds().content('The answer is 42')
        }

        ersatzServer.start()

        when:
        def request = new okhttp3.Request.Builder().url(url('/foo')).build()

        then:
        client.newCall(request).execute().body().string() == 'This is Ersatz!!'

        when:
        request = new okhttp3.Request.Builder().url(url('/foo')).build()

        then:
        client.newCall(request).execute().body().string() == 'This is another response'

        when:
        request = new okhttp3.Request.Builder().url(url("/bar")).build()
        def results = [
            client.newCall(request).execute().body().string(),
            client.newCall(request).execute().body().string()
        ]

        then:
        counter.get() == 2
        results.every { it == 'This is Bar!!' }

        when:
        request = new okhttp3.Request.Builder().url(url('/baz?alpha=42')).build()

        then:
        client.newCall(request).execute().body().string() == 'The answer is 42'

        and:
        ersatzServer.verify()
    }

    // FIXME: test this with encoder for Multipart object
    def 'multipart response text (raw)'() {
        setup:
        ersatzServer.expectations {
            get('/data') {
                responds().content(multipart {
                    boundary 't8xOJjySKePdRgBHYD'
                    encoder TEXT_PLAIN.value, CharSequence, { o -> o as String }
                    field 'alpha', 'bravo'
                    part 'file', 'data.txt', TEXT_PLAIN, 'This is some file data'
                })
            }
        }.start()

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/data')).build()).execute()

        then:
        response.body().string().trim().readLines() == '''
            --t8xOJjySKePdRgBHYD
            Content-Disposition: form-data; name="alpha"
            Content-Type: text/plain
            
            bravo
            --t8xOJjySKePdRgBHYD
            Content-Disposition: form-data; name="file"; filename="data.txt"
            Content-Type: text/plain
            
            This is some file data
            --t8xOJjySKePdRgBHYD--
        '''.stripIndent().trim().readLines()
    }

    def 'mime multipart response text (raw)'() {
        setup:
        ersatzServer.expectations {
            get('/data') {
                responds().content(mimeMultipart {
                    encoder TEXT_PLAIN.value, CharSequence, { o -> o as String }
                    field 'alpha', 'bravo'
                    part 'file', 'data.txt', TEXT_PLAIN, 'This is some file data'
                })
            }
        }.start()

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/data')).build()).execute()

        then:
        println '~~~~~~~~~~~~~~~~~~~~~'
        println response.body().string()
        //        response.body().string().trim().readLines() == '''
        //            --t8xOJjySKePdRgBHYD
        //            Content-Disposition: form-data; name="alpha"
        //            Content-Type: text/plain
        //
        //            bravo
        //            --t8xOJjySKePdRgBHYD
        //            Content-Disposition: form-data; name="file"; filename="data.txt"
        //            Content-Type: text/plain
        //
        //            This is some file data
        //            --t8xOJjySKePdRgBHYD--
        //        '''.stripIndent().trim().readLines()
    }

    def 'mime multipart response binary (raw)'() {
        setup:
        ersatzServer.expectations {
            get('/data') {
                responds().content(mimeMultipart {
                    encoder TEXT_PLAIN, CharSequence, { o -> o as String }
                    encoder 'image/jpeg', InputStream, { o -> ((InputStream) o).bytes.encodeBase64() }
                    part 'file', 'data.txt', TEXT_PLAIN, 'This is some file data'
                    part 'image', 'test-image.jpg', 'image/jpeg', ErsatzServerSpec.getResourceAsStream('/test-image.jpg'), 'base64'
                })
            }
        }.start()

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/data')).build()).execute()
        ResponseBody responseBody = response.body()

        then:
        println '~~~~~~~~~~~~~~~~~~~~~'
        println responseBody.string()

        MimeMultipart mime = new MimeMultipart(new ByteArrayDataSource(responseBody.bytes(), responseBody.contentType().toString()))

        mime.count == 2

        mime.getBodyPart(0).getHeader('Content-Disposition')[0] == 'form-data; name="file"; filename="data.txt"'
        mime.getBodyPart(0).fileName == 'data.txt'
        mime.getBodyPart(0).contentType == 'text/plain'
        mime.getBodyPart(0).inputStream.bytes.length == 22

        mime.getBodyPart(1).getHeader('Content-Disposition')[0] == 'form-data; name="image"; filename="test-image.jpg"'
        mime.getBodyPart(1).fileName == 'test-image.jpg'
        mime.getBodyPart(1).contentType == 'image/jpeg'

        byte[] bytes = mime.getBodyPart(1).inputStream.bytes
        bytes.length == ErsatzServerSpec.getResourceAsStream('/test-image.jpg').bytes.length
        bytes == ErsatzServerSpec.getResourceAsStream('/test-image.jpg').bytes
    }

    // FIXME: test this with encoder for Multipart object
    def 'multipart response binary (raw)'() {
        setup:
        ersatzServer.expectations {
            get('/data') {
                responds().content(multipart {
                    boundary 'WyAJDTEVlYgGjdI13o'
                    encoder TEXT_PLAIN, CharSequence, { o -> o as String }
                    encoder 'image/jpeg', InputStream, { o -> ((InputStream) o).bytes.encodeBase64() }
                    part 'file', 'data.txt', TEXT_PLAIN, 'This is some file data'
                    part 'image', 'test-image.jpg', 'image/jpeg', ErsatzServerSpec.getResourceAsStream('/test-image.jpg'), 'base64'
                })
            }
        }.start()

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/data')).build()).execute()
        ResponseBody responseBody = response.body()

        then:
        MimeMultipart mime = new MimeMultipart(new ByteArrayDataSource(responseBody.bytes(), responseBody.contentType().toString()))

        mime.count == 2

        mime.getBodyPart(0).getHeader('Content-Disposition')[0] == 'form-data; name="file"; filename="data.txt"'
        mime.getBodyPart(0).fileName == 'data.txt'
        mime.getBodyPart(0).contentType == 'text/plain'
        mime.getBodyPart(0).inputStream.bytes.length == 22

        mime.getBodyPart(1).getHeader('Content-Disposition')[0] == 'form-data; name="image"; filename="test-image.jpg"'
        mime.getBodyPart(1).fileName == 'test-image.jpg'
        mime.getBodyPart(1).contentType == 'image/jpeg'

        byte[] bytes = mime.getBodyPart(1).inputStream.bytes
        bytes.length == ErsatzServerSpec.getResourceAsStream('/test-image.jpg').bytes.length
        bytes == ErsatzServerSpec.getResourceAsStream('/test-image.jpg').bytes
    }

    def 'alternate construction'() {
        setup:
        def server = new ErsatzServer({
            expectations {
                get(startsWith('/hello')).responds().content('ok')
            }
        })

        server.start()

        expect:
        "${server.httpUrl}/hello/there".toURL().text == 'ok'

        cleanup:
        server.stop()
    }

    def 'gzip compression supported'() {
        setup:
        ersatzServer.expectations {
            get('/gzip').header('Accept-Encoding', 'gzip').responds().content('x' * 1000, TEXT_PLAIN)
        }

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/gzip')).build()).execute()

        then:
        response.code() == 200
        response.networkResponse().headers('Content-Encoding').contains('gzip')
    }

    def 'non-compression supported'() {
        setup:
        ersatzServer.expectations {
            get('/gzip').header('Accept-Encoding', '').responds().content('x' * 1000, TEXT_PLAIN)
        }

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/gzip')).header('Accept-Encoding', '').build()).execute()

        then:
        response.code() == 200
        !response.networkResponse().headers('Content-Encoding').contains('gzip')
    }

    def 'deflate supported'() {
        setup:
        ersatzServer.expectations {
            get('/gzip').header('Accept-Encoding', 'deflate').responds().content('x' * 1000, TEXT_PLAIN)
        }

        when:
        okhttp3.Response response = client.newCall(new okhttp3.Request.Builder().get().url(url('/gzip')).header('Accept-Encoding', 'deflate').build()).execute()

        then:
        response.code() == 200
        response.networkResponse().headers('Content-Encoding').contains('deflate')
    }

    private String url(final String path) {
        "http://localhost:${ersatzServer.httpPort}${path}"
    }
}