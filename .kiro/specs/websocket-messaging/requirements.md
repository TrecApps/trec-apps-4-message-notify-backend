# Requirements Document

## Introduction

This feature migrates the messaging section of the Spring Boot application from HTTP polling to WebSocket-based real-time updates. Currently, clients poll the `/Messages/latest` endpoint to discover new conversation activity. The new design replaces polling with a persistent STOMP-over-WebSocket connection so that the server can push events to connected clients immediately when a conversation changes.

Azure Event Hubs (Kafka-compatible API) is used as the inter-instance event bus. When any application instance handles an API call that mutates a conversation, it publishes a `ConversationEvent` to Event Hubs. Every running instance consumes from Event Hubs and forwards the event to any WebSocket client it holds that belongs to the affected conversation. If no instance holds a relevant connection, the event is silently dropped — guaranteed delivery to the client is not required.

The four mutation types that trigger push events are: new message, message seen, message reaction, and message edit.

---

## Glossary

- **WebSocket_Server**: The Spring Boot application component that accepts and manages STOMP-over-WebSocket connections from clients.
- **Kafka_Producer**: The application component responsible for publishing `ConversationEvent` messages to the Azure Event Hubs Kafka-compatible endpoint.
- **Kafka_Consumer**: The application component responsible for consuming `ConversationEvent` messages from the Azure Event Hubs Kafka-compatible endpoint.
- **ConversationEvent**: A JSON-serialisable message that describes a single mutation to a conversation. It carries an `eventType`, the `conversationId`, the `actorProfileId`, and a `payload` containing the relevant changed data.
- **EventType**: An enumeration of the four mutation kinds: `NEW_MESSAGE`, `MESSAGE_SEEN`, `MESSAGE_REACTION`, `MESSAGE_EDIT`.
- **STOMP_Session**: An active WebSocket connection between a client and the WebSocket_Server, authenticated and associated with a specific `profileId`.
- **Session_Registry**: The in-memory map maintained by each application instance that associates a `profileId` with its active STOMP_Sessions on that instance.
- **Conversation**: An existing MongoDB document (collection `conversation`) that holds the set of participant `profileId` values.
- **Message**: An existing MongoDB document (collection `message`) representing a single message within a Conversation.
- **Client**: A front-end application that connects to the WebSocket_Server via STOMP over WebSocket.

---

## Requirements

### Requirement 1: WebSocket Connection Lifecycle

**User Story:** As a client application, I want to establish a persistent WebSocket connection to the server, so that I can receive real-time conversation updates without polling.

#### Acceptance Criteria

1. THE WebSocket_Server SHALL expose a STOMP WebSocket endpoint at `/ws`.
2. WHEN a Client sends a STOMP `CONNECT` frame with a valid authentication token, THE WebSocket_Server SHALL authenticate the connection and register the resulting STOMP_Session in the Session_Registry under the authenticated `profileId`.
3. IF a Client sends a STOMP `CONNECT` frame with an invalid or missing authentication token, THEN THE WebSocket_Server SHALL reject the connection with a STOMP `ERROR` frame and close the underlying WebSocket.
4. WHEN a STOMP_Session is disconnected for any reason, THE WebSocket_Server SHALL remove that STOMP_Session from the Session_Registry.
5. THE WebSocket_Server SHALL allow a single `profileId` to hold multiple concurrent STOMP_Sessions (e.g., multiple browser tabs).

---

### Requirement 2: Client Subscription to Conversation Updates

**User Story:** As a client application, I want to subscribe to updates for a specific conversation, so that I receive only the events relevant to conversations I am viewing.

#### Acceptance Criteria

1. WHEN a Client sends a STOMP `SUBSCRIBE` frame to the destination `/user/queue/conversations/{conversationId}`, THE WebSocket_Server SHALL record that the STOMP_Session is interested in events for that `conversationId`.
2. WHEN a Client sends a STOMP `UNSUBSCRIBE` frame for a previously subscribed destination, THE WebSocket_Server SHALL remove that subscription from the STOMP_Session.
3. IF a Client attempts to subscribe to a `conversationId` for which the authenticated `profileId` is not a participant, THEN THE WebSocket_Server SHALL send a STOMP `ERROR` frame to that session and not register the subscription.
4. WHEN a STOMP_Session is disconnected, THE WebSocket_Server SHALL remove all subscriptions associated with that session.

---

### Requirement 3: Event Publication on Conversation Mutation

**User Story:** As the system, I want every conversation-mutating API call to publish a ConversationEvent, so that all application instances can be informed of the change.

#### Acceptance Criteria

1. WHEN the `POST /Messages` endpoint successfully persists a new Message, THE Kafka_Producer SHALL publish a `ConversationEvent` with `eventType` = `NEW_MESSAGE`, the `conversationId`, the `actorProfileId`, and the full serialised `Message` as the payload.
2. WHEN the `PATCH /Messages/seen` endpoint successfully updates message read status, THE Kafka_Producer SHALL publish a `ConversationEvent` with `eventType` = `MESSAGE_SEEN`, the `conversationId`, the `actorProfileId`, and the list of updated message IDs as the payload.
3. WHEN the `PATCH /Messages/react` endpoint successfully records a reaction, THE Kafka_Producer SHALL publish a `ConversationEvent` with `eventType` = `MESSAGE_REACTION`, the `conversationId`, the `actorProfileId`, and the updated `Message` as the payload.
4. WHEN the `PUT /Messages` endpoint successfully persists an edited Message, THE Kafka_Producer SHALL publish a `ConversationEvent` with `eventType` = `MESSAGE_EDIT`, the `conversationId`, the `actorProfileId`, and the updated `Message` as the payload.
5. IF the Kafka_Producer fails to publish a `ConversationEvent`, THEN THE Kafka_Producer SHALL log the failure including the `conversationId` and `eventType`, and the originating HTTP response SHALL still be returned to the caller without error.

---

### Requirement 4: Event Consumption and WebSocket Delivery

**User Story:** As a connected client, I want to receive push notifications when a conversation I am subscribed to changes, so that I see updates in real time without polling.

#### Acceptance Criteria

1. WHEN the Kafka_Consumer receives a `ConversationEvent`, THE Kafka_Consumer SHALL look up all STOMP_Sessions in the local Session_Registry that belong to participants of the affected Conversation and are subscribed to that `conversationId`.
2. WHEN at least one matching STOMP_Session is found, THE Kafka_Consumer SHALL forward the `ConversationEvent` as a STOMP `MESSAGE` frame to the destination `/user/queue/conversations/{conversationId}` for each matching session.
3. WHEN no matching STOMP_Session exists on the consuming instance, THE Kafka_Consumer SHALL discard the event without error or retry.
4. THE Kafka_Consumer SHALL process events from all application instances, including the instance that originally published the event.
5. IF forwarding a `ConversationEvent` to a STOMP_Session fails, THEN THE Kafka_Consumer SHALL log the failure and continue processing subsequent events.

---

### Requirement 5: ConversationEvent Serialisation

**User Story:** As the system, I want ConversationEvents to be reliably serialised and deserialised across the Kafka topic, so that no data is lost between producer and consumer instances.

#### Acceptance Criteria

1. THE Kafka_Producer SHALL serialise each `ConversationEvent` to JSON before publishing to the Event Hubs topic.
2. THE Kafka_Consumer SHALL deserialise each consumed record from JSON into a `ConversationEvent` before processing.
3. FOR ALL valid `ConversationEvent` objects, serialising then deserialising SHALL produce an object equal to the original (round-trip property).
4. IF the Kafka_Consumer receives a record that cannot be deserialised into a `ConversationEvent`, THEN THE Kafka_Consumer SHALL log the raw record content and skip it without crashing.

---

### Requirement 6: Authentication and Authorisation for WebSocket Connections

**User Story:** As a system operator, I want WebSocket connections to be subject to the same authentication rules as HTTP endpoints, so that unauthenticated or unauthorised users cannot receive conversation data.

#### Acceptance Criteria

1. THE WebSocket_Server SHALL require a valid authentication token on every STOMP `CONNECT` frame before granting a STOMP_Session.
2. WHILE a STOMP_Session is active, THE WebSocket_Server SHALL associate all messages sent on that session with the authenticated `profileId` established at connection time.
3. THE WebSocket_Server SHALL permit only clients with the `TREC_VERIFIED` authority to establish STOMP_Sessions, consistent with the authority required by the existing `/Messages/**` HTTP endpoints.

---

### Requirement 7: Multi-Instance Kafka Topic Configuration

**User Story:** As a system operator, I want each application instance to consume all events from the shared Kafka topic, so that every instance can serve its locally connected clients regardless of which instance published the event.

#### Acceptance Criteria

1. THE Kafka_Consumer SHALL use a unique consumer group ID per application instance so that every instance receives every event published to the topic.
2. THE WebSocket_Server SHALL expose a configuration property `trecapps.messaging.kafka.topic` that specifies the Event Hubs topic name used for `ConversationEvent` messages.
3. THE WebSocket_Server SHALL expose a configuration property `trecapps.messaging.kafka.bootstrap-servers` that specifies the Azure Event Hubs Kafka-compatible bootstrap server address.
4. WHERE the `trecapps.messaging.websocket.enabled` property is set to `false`, THE WebSocket_Server SHALL not initialise the STOMP endpoint, Kafka_Producer, or Kafka_Consumer, allowing the application to run in HTTP-only mode.

---

### Requirement 8: Backward Compatibility with HTTP Polling

**User Story:** As a client application that has not yet migrated to WebSocket, I want the existing HTTP polling endpoints to remain functional, so that I can migrate incrementally.

#### Acceptance Criteria

1. THE WebSocket_Server SHALL preserve the existing `GET /Messages`, `GET /Messages/latest`, `POST /Messages`, `PATCH /Messages/seen`, `PATCH /Messages/react`, and `PUT /Messages` HTTP endpoints without modification to their request or response contracts.
2. WHEN a `ConversationEvent` is published, THE Kafka_Producer SHALL publish it in addition to completing the normal HTTP response, not as a replacement.
