# Implementation Plan: WebSocket Messaging

## Overview

Implement a WebSocket-based real-time push delivery layer on top of the existing HTTP messaging API. The implementation is additive — all existing HTTP endpoints remain unchanged. The feature is gated behind `trecapps.messaging.websocket.enabled`. Inter-instance fan-out uses Azure Event Hubs via the Kafka-compatible API. Sessions are tracked in-memory per instance using `SessionRegistry` and `SubscriptionRegistry`.

## Tasks

- [x] 1. Add dependencies and configuration properties
  - Add `spring-kafka` and `spring-boot-starter-websocket` to `build.gradle`
  - Add `net.jqwik:jqwik` test dependency to `build.gradle`
  - Add `spring-kafka-test` test dependency to `build.gradle`
  - Add the following properties to `application-message.properties`:
    - `trecapps.messaging.websocket.enabled=false`
    - `trecapps.messaging.kafka.topic=${KAFKA_TOPIC:conversation-events}`
    - `trecapps.messaging.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}`
    - `trecapps.messaging.kafka.connection-string=${KAFKA_CONNECTION_STRING:}`
  - _Requirements: 7.2, 7.3, 7.4_

- [x] 2. Implement `ConversationEvent` data model
  - [x] 2.1 Create `EventType` enum and `ConversationEvent` class
    - Create `src/main/java/com/trecapps/comm/messages/models/EventType.java` with values `NEW_MESSAGE`, `MESSAGE_SEEN`, `MESSAGE_REACTION`, `MESSAGE_EDIT`
    - Create `src/main/java/com/trecapps/comm/messages/models/ConversationEvent.java` as a Lombok `@Data` / `@NoArgsConstructor` / `@AllArgsConstructor` class with fields: `EventType eventType`, `UUID conversationId`, `UUID actorProfileId`, `Object payload`
    - Ensure Jackson can serialise/deserialise the class (add `@JsonTypeInfo` or configure `ObjectMapper` as needed for the `Object payload` field)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 5.1, 5.2_

  - [ ]* 2.2 Write property test for `ConversationEvent` serialisation round-trip
    - **Property 9: ConversationEvent serialisation round-trip**
    - Generate random `ConversationEvent` instances across all four `EventType` variants with random UUIDs and payloads; serialise to JSON then deserialise; assert equality
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - `// Feature: websocket-messaging, Property 9: ConversationEvent serialisation round-trip`

  - [ ]* 2.3 Write unit test for invalid JSON handling in consumer (placeholder — consumer not yet implemented; revisit at task 6)
    - Verify that a raw byte sequence that is not valid `ConversationEvent` JSON does not cause an exception when passed through the deserialisation path
    - _Requirements: 5.4_

- [ ] 3. Implement `SessionRegistry` and `SubscriptionRegistry`
  - [x] 3.1 Implement `SessionRegistry`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/SessionRegistry.java`
    - Annotate with `@Component` and `@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")`
    - Internal state: `ConcurrentHashMap<UUID, CopyOnWriteArraySet<String>>` (profileId → sessionIds) and a reverse map `ConcurrentHashMap<String, UUID>` (sessionId → profileId)
    - Implement `void register(UUID profileId, String sessionId)`, `void deregister(String sessionId)`, `Set<String> getSessionIds(UUID profileId)`
    - `deregister` must remove the profileId entry when its session set becomes empty
    - _Requirements: 1.2, 1.4, 1.5_

  - [ ]* 3.2 Write property test for `SessionRegistry` — session registration on connect
    - **Property 1: Session registration on connect**
    - Generate random `profileId` + session ID, call `register`, assert `getSessionIds` contains the session ID
    - **Validates: Requirements 1.2**
    - `// Feature: websocket-messaging, Property 1: Session registration on connect`

  - [ ]* 3.3 Write property test for `SessionRegistry` — multiple sessions per profileId
    - **Property 3: Multiple sessions per profileId**
    - Generate random `profileId` and N ≥ 2 session IDs, register all, assert all are present in `getSessionIds`
    - **Validates: Requirements 1.5**
    - `// Feature: websocket-messaging, Property 3: Multiple sessions per profileId`

  - [ ]* 3.4 Write unit tests for `SessionRegistry`
    - Test `deregister` of unknown session is a no-op
    - Test `deregister` removes the profileId entry when the last session is removed
    - Test concurrent `register`/`deregister` does not corrupt state
    - _Requirements: 1.4, 1.5_

  - [x] 3.5 Implement `SubscriptionRegistry`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/SubscriptionRegistry.java`
    - Annotate with `@Component` and `@ConditionalOnProperty(...)`
    - Internal state: `ConcurrentHashMap<String, Set<UUID>>` (sessionId → conversationIds)
    - Implement `void subscribe(String sessionId, UUID conversationId)`, `void unsubscribe(String sessionId, UUID conversationId)`, `void removeSession(String sessionId)`, `boolean isSubscribed(String sessionId, UUID conversationId)`
    - _Requirements: 2.1, 2.2, 2.4_

  - [ ]* 3.6 Write property test for `SubscriptionRegistry` — subscribe/unsubscribe round-trip
    - **Property 4: Subscription registration and removal round-trip**
    - Generate random session ID and `conversationId`, call `subscribe` then `unsubscribe`, assert `isSubscribed` returns `false`
    - **Validates: Requirements 2.1, 2.2**
    - `// Feature: websocket-messaging, Property 4: Subscription registration and removal round-trip`

  - [ ]* 3.7 Write unit tests for `SubscriptionRegistry`
    - Test `removeSession` clears all subscriptions for the session
    - Test concurrent access does not corrupt state
    - _Requirements: 2.4_

- [x] 4. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 5. Implement WebSocket authentication and connection lifecycle
  - [x] 5.1 Implement `WebSocketHandshakeInterceptor`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/WebSocketHandshakeInterceptor.java` implementing `HandshakeInterceptor`
    - Inject `TrecAuthSecurityAsyncParser` directly
    - In `beforeHandshake`: wrap request/response into a `DefaultServerWebExchange`, call `extractAccountInfo(exchange).block()`, check for `TREC_VERIFIED` authority
    - On failure: set response status to `401`, return `false`
    - On success: store `profileId` in `attributes` map under key `"profileId"`, return `true`
    - `afterHandshake` is a no-op
    - _Requirements: 1.2, 1.3, 6.1, 6.3_

  - [ ]* 5.2 Write property test for `WebSocketHandshakeInterceptor` — unauthorised tokens rejected
    - **Property 11: Only TREC_VERIFIED tokens may connect**
    - Mock `TrecAuthSecurityAsyncParser`; generate tokens without `TREC_VERIFIED` authority or with no token; assert `beforeHandshake` returns `false` and no session is registered
    - **Validates: Requirements 6.3, 1.3**
    - `// Feature: websocket-messaging, Property 11: Only TREC_VERIFIED tokens may connect`

  - [ ]* 5.3 Write unit tests for `WebSocketHandshakeInterceptor`
    - Valid token in `Authorization` header → `profileId` stored in attributes, returns `true`
    - Valid token in cookie (no header) → `profileId` stored in attributes, returns `true`
    - Invalid/missing token → returns `false` with `401`
    - Token present but missing `TREC_VERIFIED` authority → returns `false` with `401`
    - _Requirements: 1.2, 1.3, 6.1, 6.3_

  - [x] 5.4 Implement `StompAuthChannelInterceptor`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/StompAuthChannelInterceptor.java` implementing `ChannelInterceptor`
    - Intercept `CONNECT` frames; read `"profileId"` from `SimpMessageHeaderAccessor.getSessionAttributes()`
    - If absent: throw `MessagingException`
    - If present: call `SessionRegistry.register(profileId, sessionId)`
    - _Requirements: 1.2, 1.3_

  - [ ]* 5.5 Write unit tests for `StompAuthChannelInterceptor`
    - `profileId` present in session attributes → `SessionRegistry.register` called
    - `profileId` absent → `MessagingException` thrown
    - _Requirements: 1.2, 1.3_

  - [x] 5.6 Implement `SessionDisconnectListener`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/SessionDisconnectListener.java` implementing `ApplicationListener<SessionDisconnectEvent>`
    - On event: call `SessionRegistry.deregister(sessionId)` and `SubscriptionRegistry.removeSession(sessionId)`
    - _Requirements: 1.4, 2.4_

  - [ ]* 5.7 Write property test for session deregistration on disconnect
    - **Property 2: Session deregistration on disconnect**
    - Populate `SessionRegistry` and `SubscriptionRegistry` with random sessions and subscriptions; simulate disconnect for one session; assert session ID is absent from both registries
    - **Validates: Requirements 1.4, 2.4**
    - `// Feature: websocket-messaging, Property 2: Session deregistration on disconnect`

- [ ] 6. Implement subscription management
  - [x] 6.1 Implement `StompSubscriptionController`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/StompSubscriptionController.java`
    - Handle `SUBSCRIBE` frames to `/user/queue/conversations/{conversationId}`
    - Read `profileId` from session attributes; query `ConversationRepo` to verify the profile is a participant
    - On success: call `SubscriptionRegistry.subscribe(sessionId, conversationId)`
    - On failure: send STOMP `ERROR` frame; do not register the subscription
    - _Requirements: 2.1, 2.3_

  - [ ]* 6.2 Write property test for unauthorised subscription rejection
    - **Property 5: Unauthorised subscription rejection**
    - Mock `ConversationRepo`; generate `profileId` not in participant list; attempt subscribe; assert rejection and `SubscriptionRegistry` does not record the subscription
    - **Validates: Requirements 2.3**
    - `// Feature: websocket-messaging, Property 5: Unauthorised subscription rejection`

  - [ ]* 6.3 Write unit tests for `StompSubscriptionController`
    - Authorised participant → subscription recorded
    - Non-participant → STOMP ERROR sent, subscription not recorded
    - _Requirements: 2.1, 2.3_

- [ ] 7. Implement Kafka infrastructure
  - [x] 7.1 Implement `KafkaConfig`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/KafkaConfig.java`
    - Annotate with `@Configuration` and `@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")`
    - Define `ProducerFactory<String, String>`, `KafkaTemplate<String, String>`, `ConsumerFactory<String, String>`, and `ConcurrentKafkaListenerContainerFactory` beans
    - Read `trecapps.messaging.kafka.bootstrap-servers` and `trecapps.messaging.kafka.topic`
    - Configure SASL/SSL for Azure Event Hubs (connection string or Entra-based credential based on whether `trecapps.messaging.kafka.connection-string` is set)
    - Define a `@Bean String kafkaConsumerGroupId` that returns `"messaging-ws-" + UUID.randomUUID()` (evaluated once at startup)
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 7.2 Implement `KafkaConversationProducer`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/KafkaConversationProducer.java`
    - Annotate with `@Component` and `@ConditionalOnProperty(...)`
    - Inject `KafkaTemplate<String, String>` and `ObjectMapper`
    - Implement `void publishEvent(ConversationEvent event)`: serialise to JSON, send to configured topic with key `conversationId.toString()`
    - Catch all exceptions internally; log at `ERROR` level with `conversationId` and `eventType`; do not rethrow
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 5.1_

  - [ ]* 7.3 Write unit tests for `KafkaConversationProducer`
    - Successful publish calls `KafkaTemplate.send` with correct topic, key, and JSON value
    - `KafkaException` is caught and logged; no exception propagates to caller
    - _Requirements: 3.5_

  - [x] 7.4 Implement `KafkaConversationConsumer`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/KafkaConversationConsumer.java`
    - Annotate with `@Component` and `@ConditionalOnProperty(...)`
    - Annotate listener method with `@KafkaListener(topics = "${trecapps.messaging.kafka.topic}", groupId = "#{kafkaConsumerGroupId}")`
    - On each record: deserialise JSON to `ConversationEvent`; look up conversation participants from `ConversationRepo`; query `SessionRegistry` and `SubscriptionRegistry` for matching sessions; send event JSON to each matching session via `SimpMessagingTemplate.convertAndSendToUser`
    - On deserialisation failure: log raw record at `ERROR` level; skip record; do not rethrow
    - On delivery failure to a session: log at `WARN` level; continue
    - Extract the session-matching logic into a package-private pure method for testability
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.2, 5.4_

  - [ ]* 7.5 Write property test for consumer routing logic
    - **Property 8: Consumer routes only to subscribed participant sessions**
    - Call the extracted pure routing method with random `SessionRegistry`/`SubscriptionRegistry` state and a `ConversationEvent`; assert the returned session set equals the intersection of participants and subscribers
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - `// Feature: websocket-messaging, Property 8: Consumer routes only to subscribed participant sessions`

  - [ ]* 7.6 Write property test for consumer invalid JSON handling
    - **Property 10: Invalid JSON is skipped without crash**
    - Generate random byte sequences that are not valid `ConversationEvent` JSON; pass through the consumer's deserialisation path; assert no exception is thrown and processing continues
    - **Validates: Requirements 5.4**
    - `// Feature: websocket-messaging, Property 10: Invalid JSON is skipped without crash`

  - [ ]* 7.7 Write unit tests for `KafkaConversationConsumer`
    - Correct sessions selected for an event
    - Empty match → no send called
    - Delivery failure → logged and processing continues
    - _Requirements: 4.1, 4.2, 4.3, 4.5_

- [x] 8. Checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Modify `MessageService` to publish `ConversationEvent`
  - [x] 9.1 Inject `KafkaConversationProducer` into `MessageService`
    - Add `@Autowired(required = false) KafkaConversationProducer kafkaProducer` field to `MessageService`
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [x] 9.2 Publish `NEW_MESSAGE` event in `postMessage`
    - After `messageRepo.save(newMessage)` succeeds, call `kafkaProducer.publishEvent(new ConversationEvent(EventType.NEW_MESSAGE, conversationId, profileId, savedMessage))` if `kafkaProducer != null`
    - Wrap in `.doOnSuccess(...)` or try/catch so that a producer failure does not fail the reactive chain
    - _Requirements: 3.1, 3.5, 8.2_

  - [x] 9.3 Publish `MESSAGE_SEEN` and `MESSAGE_REACTION` events in `markReaction`
    - After `messageRepo.saveAll(messages)` succeeds, determine event type: if `reactionType == null` publish `MESSAGE_SEEN` with the list of message IDs as payload; otherwise publish `MESSAGE_REACTION` with the updated `Message` as payload
    - _Requirements: 3.2, 3.3, 3.5, 8.2_

  - [x] 9.4 Publish `MESSAGE_EDIT` event in `editMessage`
    - After `messageRepo.save(message)` succeeds, call `kafkaProducer.publishEvent(new ConversationEvent(EventType.MESSAGE_EDIT, conversationId, profileId, updatedMessage))` if `kafkaProducer != null`
    - _Requirements: 3.4, 3.5, 8.2_

  - [ ]* 9.5 Write property test for mutation → correct event type
    - **Property 6: Mutation triggers correct event type**
    - Mock `KafkaConversationProducer`; generate random valid message/conversation/profile; call each mutation method; assert producer called exactly once with correct `EventType`, matching `conversationId`, and matching `actorProfileId`
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
    - `// Feature: websocket-messaging, Property 6: Mutation triggers correct event type`

  - [ ]* 9.6 Write property test for producer failure does not fail HTTP response
    - **Property 7: Producer failure does not fail the HTTP response**
    - Configure producer mock to always throw; generate random mutation input; assert the returned `Mono<ResponseObj>` still completes with a success status
    - **Validates: Requirements 3.5**
    - `// Feature: websocket-messaging, Property 7: Producer failure does not fail the HTTP response`

  - [ ]* 9.7 Write unit tests for `MessageService` integration with producer
    - Each mutation method calls the producer with the correct `EventType` and payload
    - Producer is not called when `kafkaProducer` is `null` (HTTP-only mode)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 10. Implement `WebSocketConfig` and wire everything together
  - [x] 10.1 Implement `WebSocketConfig`
    - Create `src/main/java/com/trecapps/comm/messages/websocket/WebSocketConfig.java`
    - Annotate with `@Configuration` and `@ConditionalOnProperty(name = "trecapps.messaging.websocket.enabled", havingValue = "true")`
    - Extend `WebSocketMessageBrokerConfigurer`; register STOMP endpoint at `/ws` (no SockJS fallback); register `WebSocketHandshakeInterceptor`
    - Configure `StompAuthChannelInterceptor` on the inbound channel
    - Register `SessionDisconnectListener` as an `ApplicationListener<SessionDisconnectEvent>` bean
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 7.4_

  - [x] 10.2 Update `SecurityConfig` to permit `/ws/**`
    - Add `/ws/**` to the `permitAll()` matcher in `SecurityConfig.securityWebFilterChain` so the HTTP upgrade request is not blocked before the handshake interceptor runs
    - _Requirements: 6.1, 6.3_

  - [ ]* 10.3 Write smoke tests for conditional bean presence
    - Verify `WebSocketConfig` registers the `/ws` endpoint when `trecapps.messaging.websocket.enabled=true`
    - Verify no WebSocket or Kafka beans are present in the Spring context when `trecapps.messaging.websocket.enabled=false`
    - Verify the Kafka consumer group ID is unique across two instantiations of `KafkaConfig`
    - _Requirements: 7.1, 7.4_

  - [ ]* 10.4 Write integration test using embedded Kafka
    - Use `spring-kafka-test` embedded broker; post a message via `MessageService`; assert a `ConversationEvent` is produced to the topic; assert the consumer delivers it to a mock STOMP session
    - _Requirements: 3.1, 4.1, 4.2_

  - [ ]* 10.5 Write integration tests for existing HTTP endpoint contracts
    - Use `WebTestClient` to verify `GET /Messages`, `POST /Messages`, `PATCH /Messages/seen`, `PATCH /Messages/react`, `PUT /Messages`, `GET /Messages/latest` return the same response contracts as before
    - _Requirements: 8.1_

- [x] 11. Final checkpoint — Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use [jqwik](https://jqwik.net/) (JUnit 5 compatible); each is tagged with `// Feature: websocket-messaging, Property N: ...`
- The entire WebSocket/Kafka subsystem is gated behind `trecapps.messaging.websocket.enabled=false` by default — the application continues to run in HTTP-only mode until the flag is enabled
- `KafkaConversationProducer` is injected into `MessageService` with `required = false` so the service compiles and runs without Kafka when the flag is off
- All new classes live under `src/main/java/com/trecapps/comm/messages/websocket/`
