/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.kafka.clients;

import brave.ScopedSpan;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class TracingProducerTest extends BaseTracingTest {
  MockProducer<Object, String> mockProducer = new MockProducer<>();
  KafkaTracing kafkaTracing = KafkaTracing.create(tracing);
  TracingProducer<Object, String> tracingProducer =
    (TracingProducer<Object, String>) kafkaTracing.producer(mockProducer);

  @Test public void should_add_b3_headers_to_records() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

    List<String> headerKeys = mockProducer.history().stream()
      .flatMap(records -> Arrays.stream(records.headers().toArray()))
      .map(Header::key)
      .collect(Collectors.toList());

    assertThat(headerKeys).containsOnly("b3");
  }

  @Test public void should_add_b3_headers_when_other_headers_exist() throws InterruptedException {
    ProducerRecord<Object, String> record = new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE);
    record.headers().add("tx-id", "1".getBytes());
    tracingProducer.send(record);
    mockProducer.completeNext();

    Span producerSpan = takeSpan(spans);
    assertThat(lastHeaders(mockProducer))
      .containsEntry("tx-id", "1")
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");
  }

  @Test public void should_inject_child_context() throws InterruptedException {
    ScopedSpan scopedSpan = tracing.tracer().startScopedSpan("main");
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    mockProducer.completeNext();
    scopedSpan.finish();

    Span producerSpan = takeSpan(spans);
    assertThat(lastHeaders(mockProducer))
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");

    assertThat(producerSpan.parentId())
      .isEqualTo(takeSpan(spans).id());
  }

  @Test public void should_add_parent_trace_when_context_injected_on_headers()
    throws InterruptedException {
    brave.Span span = tracing.tracer().newTrace().start();
    ProducerRecord<Object, String> record = new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE);
    tracingProducer.injector.inject(span.context(), new KafkaProducerRequest(record));
    tracingProducer.send(record);
    mockProducer.completeNext();
    span.finish();

    Span producerSpan = takeSpan(spans);
    assertThat(lastHeaders(mockProducer))
      .containsEntry("b3", producerSpan.traceId() + "-" + producerSpan.id() + "-1");

    assertThat(producerSpan.parentId())
      .isEqualTo(takeSpan(spans).id());
  }

  @Test public void should_call_wrapped_producer() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));

    assertThat(mockProducer.history()).hasSize(1);
  }

  @Test public void send_should_set_name() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
      .flatExtracting(Span::name)
      .containsOnly("send");
  }

  @Test public void send_should_tag_topic_and_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", TEST_TOPIC), entry("kafka.key", TEST_KEY));
  }

  @Test public void send_shouldnt_tag_null_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, null, TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }

  @Test public void send_shouldnt_tag_binary_key() {
    tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, new byte[1], TEST_VALUE));
    mockProducer.completeNext();

    assertThat(spans)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(entry("kafka.topic", TEST_TOPIC));
  }
}
