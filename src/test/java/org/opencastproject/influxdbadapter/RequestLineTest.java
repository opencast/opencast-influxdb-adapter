package org.opencastproject.influxdbadapter;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

class RequestLineTest {

  @ParameterizedTest
  @ValueSource(strings = {
          "GET /mh_default_org/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
          "GET /mh_default_org/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.webm HTTP/1.1",
          "GET /mh_default_org/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp3 HTTP/1.1",
  })
  void testRequestLinePatternWithValidFileExtensions(String line) {
    Optional<RequestLine> requestLine = RequestLine.parseLine(line);
    Assertions.assertThat(requestLine.isPresent()).isEqualTo(true);
    Assertions.assertThat(requestLine.get().getMethod()).isEqualTo("GET");
    Assertions.assertThat(requestLine.get().getOrganizationId()).isEqualTo("mh_default_org");
    Assertions.assertThat(requestLine.get().getPublicationChannel()).isEqualTo("oaipmh-default");
    Assertions.assertThat(requestLine.get().getEpisodeId()).isEqualTo("5a990722-6f18-4c69-ac84-4721934cb58b");
    Assertions.assertThat(requestLine.get().getAssetId()).isEqualTo("c5f2ac27-0da1-4d91-952c-771905058ef5");
  }

  @ParameterizedTest
  @ValueSource(strings = {
          "GET /mh_default_org/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
          "GET /my_org_edu/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
  })
  void testRequestLinePatternWithDifferentOrgIds(String line) {
    Optional<RequestLine> requestLine = RequestLine.parseLine(line);
    Assertions.assertThat(requestLine.isPresent()).isEqualTo(true);
    Assertions.assertThat(requestLine.get().getMethod()).isEqualTo("GET");
    Assertions.assertThat(requestLine.get().getOrganizationId()).isIn("mh_default_org", "my_org_edu");
    Assertions.assertThat(requestLine.get().getPublicationChannel()).isEqualTo("oaipmh-default");
    Assertions.assertThat(requestLine.get().getEpisodeId()).isEqualTo("5a990722-6f18-4c69-ac84-4721934cb58b");
    Assertions.assertThat(requestLine.get().getAssetId()).isEqualTo("c5f2ac27-0da1-4d91-952c-771905058ef5");
  }

  @ParameterizedTest
  @ValueSource(strings = {
          "GET /mh_default_org/oaipmh-default/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
          "GET /mh_default_org/oaipmh-library/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
          "GET /mh_default_org/oaipmh-portal/5a990722-6f18-4c69-ac84-4721934cb58b/c5f2ac27-0da1-4d91-952c-771905058ef5/myvideo.mp4 HTTP/1.1",
  })
  void testRequestLinePatternWithDifferentPublicationChannels(String line) {
    Optional<RequestLine> requestLine = RequestLine.parseLine(line);
    Assertions.assertThat(requestLine.isPresent()).isEqualTo(true);
    Assertions.assertThat(requestLine.get().getMethod()).isEqualTo("GET");
    Assertions.assertThat(requestLine.get().getOrganizationId()).isEqualTo("mh_default_org");
    Assertions.assertThat(requestLine.get().getPublicationChannel()).isIn("oaipmh-default", "oaipmh-library", "oaipmh-portal");
    Assertions.assertThat(requestLine.get().getEpisodeId()).isEqualTo("5a990722-6f18-4c69-ac84-4721934cb58b");
    Assertions.assertThat(requestLine.get().getAssetId()).isEqualTo("c5f2ac27-0da1-4d91-952c-771905058ef5");
  }
}
