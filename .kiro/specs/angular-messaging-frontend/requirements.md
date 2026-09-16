# Requirements Document

## Introduction

This feature is a standalone Angular 21 messaging frontend application located in the `frontend-base` folder. The application allows authenticated users to view their conversations and exchange messages in real time. The main page displays a list of conversations the user participates in. Clicking a conversation opens a floating chat panel — similar to the Facebook or LinkedIn messenger experience — that shows the conversation's message history, connects to the backend via STOMP over WebSocket, and updates immediately when events arrive. Multiple panels may be open at the same time.

The application integrates with the existing backend API (conversation and message REST endpoints plus the STOMP WebSocket endpoint at `/ws`) and reuses the shared `AuthService`, `StylesService`, and `environment` patterns already established in the project's helper files.

---

## Glossary

- **App**: The Angular 21 standalone-component application built in the `frontend-base` folder.
- **AuthService**: The existing injectable Angular service that holds the authenticated `AccountList` signal, performs session refresh, and handles login/logout navigation.
- **Messaging_Service**: The Angular injectable service responsible for all HTTP calls to the conversation and message REST endpoints.
- **WebSocket_Service**: The Angular injectable service responsible for establishing and managing a single STOMP over WebSocket connection to the backend `/ws` endpoint and dispatching incoming `ConversationEvent` objects to subscribers.
- **Conversation_List_Page**: The routed page component at `/home` that displays all conversations the authenticated user participates in.
- **Chat_Panel**: A floating overlay component that opens when the user selects a conversation, shows the message history, and allows the user to send, edit, react to, and mark messages as seen.
- **ConversationEvent**: A JSON object pushed by the backend over WebSocket with fields `eventType`, `conversationId`, `actorProfileId`, and `payload`.
- **EventType**: An enumeration of the four event kinds: `NEW_MESSAGE`, `MESSAGE_SEEN`, `MESSAGE_REACTION`, `MESSAGE_EDIT`.
- **Conversation**: A data object with fields `id`, `participants` (array of profile IDs), `appId`, and optional `latestActivity` (the timestamp of the most recent message in the conversation).
- **Message**: A data object with fields `id`, `conversationId`, `authorId`, `content`, `timestamp`, `seen` (array of profile IDs), `versions`, and `reactions`.
- **Account**: The authenticated user's profile object with fields `id`, `displayName`, `accountHandle`, and `type`.
- **STOMP_Connection**: The persistent WebSocket session managed by `WebSocket_Service` using `@stomp/stompjs`.
- **appId**: The application identifier read from `environment.app_name` and passed as a query parameter to the `GET /Conversations` and `POST /Conversations` endpoints.
- **Focused_Panel**: A Chat_Panel that has most recently received a user interaction (click, scroll, or text input). The focused panel is visually distinguished and is the active keyboard-input receiver.

---

## Requirements

### Requirement 1: Application Bootstrap and Authentication

**User Story:** As a user, I want the application to check my session on load, so that I am taken directly to my conversations if already logged in, or redirected to the login page if not.

#### Acceptance Criteria

1. WHEN the App initialises, THE AuthService SHALL call `attemptRefresh()` to validate the existing session cookie.
2. WHILE `attemptRefresh()` is in-flight, THE App SHALL display a loading/splash state and SHALL NOT render the Conversation_List_Page or navigate to `/logon`.
3. IF `attemptRefresh()` completes and the `AuthService.account` signal remains `undefined`, THEN THE App SHALL navigate the user to the `/logon` route.
4. WHEN `attemptRefresh()` succeeds and `AuthService.account` is set to a non-null `AccountList`, THE App SHALL navigate the user to the `/home` route.
5. THE App SHALL expose a `/logon` route that renders the login component provided in the helper files.
6. THE App SHALL expose a `/home` route that renders the Conversation_List_Page.
7. THE App SHALL protect the `/home` route with a route guard that redirects users to `/logon` when `AuthService.account` is `undefined`.

---

### Requirement 2: Conversation List Display

**User Story:** As an authenticated user, I want to see a list of all conversations I participate in on the main page, so that I can quickly find and open a conversation.

#### Acceptance Criteria

1. WHEN the Conversation_List_Page loads, THE Messaging_Service SHALL call `GET /Conversations?appId={appId}` using the `appId` value from `environment.app_name`.
2. WHEN the `GET /Conversations` response is received, THE Conversation_List_Page SHALL display one entry per Conversation, showing at minimum the conversation ID and participant count.
3. WHEN the conversation list is loading, THE Conversation_List_Page SHALL display a loading indicator and SHALL NOT show stale conversation entries.
4. IF the `GET /Conversations` request returns an error, THEN THE Conversation_List_Page SHALL display an error message and a retry button that re-issues the `GET /Conversations` request when clicked.
5. WHEN the `GET /Conversations` response is received, THE Conversation_List_Page SHALL sort conversations by `latestActivity` in descending order; conversations with no `latestActivity` SHALL appear after all conversations that have one.
6. WHERE a Conversation has a `latestActivity` value, THE Conversation_List_Page SHALL display it in a human-readable relative format (e.g., "2 minutes ago").

---

### Requirement 3: Opening and Closing Chat Panels

**User Story:** As a user, I want to open a floating chat panel for a conversation by clicking it in the list, so that I can read and send messages without leaving the conversation list.

#### Acceptance Criteria

1. WHEN the user clicks a conversation entry, THE App SHALL open a Chat_Panel for that conversation anchored to the bottom of the viewport; new panels SHALL open to the left of any existing open panels.
2. THE App SHALL allow up to 3 Chat_Panels to be open simultaneously.
3. IF the user attempts to open a fourth Chat_Panel when 3 are already open, THEN THE App SHALL close the Chat_Panel with the longest time elapsed since its last user interaction (click, scroll, or text input) before opening the new one.
4. WHEN the user clicks the close button on a Chat_Panel, THE App SHALL remove that panel from the viewport and the WebSocket_Service SHALL send a STOMP UNSUBSCRIBE frame for that conversation.
5. WHEN a Chat_Panel is opened for a conversation that already has an open panel, THE App SHALL move that existing panel to the rightmost position and make it the Focused_Panel rather than opening a duplicate.
6. THE App SHALL visually distinguish the Focused_Panel from non-focused panels (e.g., via an elevated border or header highlight).
7. A Chat_Panel SHALL become the Focused_Panel when the user clicks its header, clicks within its message area, scrolls within it, or types in its text input.

---

### Requirement 4: Message History Loading

**User Story:** As a user, I want to see the recent message history when I open a conversation panel, so that I have context for the conversation.

#### Acceptance Criteria

1. WHEN a Chat_Panel opens, THE Messaging_Service SHALL call `GET /Messages?conversationId={id}&page=0` to fetch the first page of messages.
2. WHILE the initial message history is loading, THE Chat_Panel SHALL display a loading indicator and SHALL NOT show a partially populated message list.
3. THE Chat_Panel SHALL display messages in chronological order with the oldest messages at the top and the newest at the bottom.
4. WHEN the user scrolls to the top of the message list and the currently loaded page index is greater than 0, THE Messaging_Service SHALL call `GET /Messages?conversationId={id}&page={n}` where `{n}` is one less than the lowest-loaded page index, and prepend the returned messages to the list.
5. WHILE an additional page of messages is loading, THE Chat_Panel SHALL display a loading indicator at the top of the message list.
6. IF the lowest-loaded page index equals 0, THEN THE Chat_Panel SHALL not display a further loading trigger at the top of the message list.
7. IF a message history fetch request fails (initial or paginated), THEN THE Chat_Panel SHALL hide the loading indicator and display an inline error message indicating the failure.

---

### Requirement 5: WebSocket Connection Lifecycle

**User Story:** As a user, I want the application to maintain a single WebSocket connection to the backend so that all open chat panels receive real-time updates efficiently.

#### Acceptance Criteria

1. IF the user is authenticated (`AuthService.account` is non-null), THEN WHEN the user navigates to `/home`, THE WebSocket_Service SHALL establish a single STOMP_Connection to the URL formed by appending `/ws` to `environment.message_service_url`.
2. THE WebSocket_Service SHALL pass credentials via the browser session cookie (using `withCredentials: true` on the underlying WebSocket) so the backend can authenticate the STOMP CONNECT frame.
3. WHEN the STOMP_Connection is lost unexpectedly, THE WebSocket_Service SHALL attempt to reconnect using an exponential back-off starting at 5 seconds, doubling on each attempt up to a maximum interval of 60 seconds, and SHALL stop after 10 consecutive failed attempts.
4. WHEN the user navigates to any route outside `/home`, THE WebSocket_Service SHALL disconnect the STOMP_Connection and cancel all active subscriptions.
5. WHEN the user logs out, THE WebSocket_Service SHALL disconnect the STOMP_Connection and cancel all active subscriptions.
6. THE WebSocket_Service SHALL use a single shared STOMP_Connection for all Chat_Panels rather than opening a separate connection per panel.
7. IF the initial STOMP_Connection attempt fails (not a mid-session drop), THEN THE WebSocket_Service SHALL apply the same exponential back-off retry strategy defined in criterion 3 and SHALL surface a connection-failed status to all open Chat_Panels after 10 failed attempts.

---

### Requirement 6: Subscribing to Conversation Updates

**User Story:** As a user viewing a conversation panel, I want the panel to subscribe to live updates for that conversation, so that new messages and reactions appear without requiring a manual refresh.

#### Acceptance Criteria

1. WHEN a Chat_Panel opens, THE WebSocket_Service SHALL send a STOMP SUBSCRIBE frame to `/user/queue/conversations/{conversationId}` for the panel's conversation.
2. WHEN a Chat_Panel is closed, THE WebSocket_Service SHALL send a STOMP UNSUBSCRIBE frame for the corresponding `/user/queue/conversations/{conversationId}` destination.
3. WHEN a STOMP MESSAGE frame arrives on a subscribed destination, THE WebSocket_Service SHALL deserialise the body as a `ConversationEvent` and deliver it to the Chat_Panel whose `conversationId` matches the event's `conversationId`.
4. WHILE the STOMP_Connection is not yet established, THE WebSocket_Service SHALL queue subscription requests up to a maximum of 50 pending requests; WHEN the connection is established, THE WebSocket_Service SHALL drain the queue and send each pending SUBSCRIBE frame.
5. IF the queue reaches 50 pending requests before the connection is established, or if 30 seconds elapse without a connection, THEN THE WebSocket_Service SHALL discard the pending queue and notify each affected Chat_Panel that the subscription failed.
6. IF the backend responds to a SUBSCRIBE frame with a STOMP ERROR frame, THEN THE WebSocket_Service SHALL mark the corresponding Chat_Panel as subscription-failed and deliver an error indication to it.

---

### Requirement 7: Handling NEW_MESSAGE Events

**User Story:** As a user, I want newly received messages to appear in the chat panel immediately when the backend pushes them, so that I see the conversation in real time.

#### Acceptance Criteria

1. WHEN the WebSocket_Service delivers a `ConversationEvent` with `eventType` = `NEW_MESSAGE` to a Chat_Panel and the event payload is a valid `Message` object, THE Chat_Panel SHALL append that `Message` to the bottom of the displayed message list.
2. IF the `NEW_MESSAGE` event payload is absent or cannot be parsed as a `Message`, THEN THE Chat_Panel SHALL log the error and SHALL NOT modify the displayed message list.
3. WHEN the Chat_Panel receives a new message and the user's scroll position is within 100 pixels of the bottom of the message list, THE Chat_Panel SHALL scroll the view to show the newly appended message.
4. WHEN the Chat_Panel receives a new message and the user's scroll position is more than 100 pixels from the bottom, THE Chat_Panel SHALL display a notification badge showing the count of unread new messages and SHALL NOT scroll automatically.
5. WHEN the user clicks the unread message notification badge, THE Chat_Panel SHALL scroll to the bottom of the message list and reset the badge count to zero.
6. WHEN a `NEW_MESSAGE` event is received for a conversation whose Chat_Panel is not open, THE Conversation_List_Page SHALL update that conversation's `latestActivity` display and increment any unread message count indicator on the conversation entry.

---

### Requirement 8: Handling MESSAGE_SEEN Events

**User Story:** As a user, I want to see when my messages have been read by other participants, so that I know my messages are being received.

#### Acceptance Criteria

1. WHEN the WebSocket_Service delivers a `ConversationEvent` with `eventType` = `MESSAGE_SEEN` to a Chat_Panel, THE Chat_Panel SHALL update the seen indicator on each displayed `Message` whose `id` appears in the event payload list.
2. THE Chat_Panel SHALL display a read receipt indicator (e.g., a checkmark icon or participant avatar) beneath each message that has at least one entry in its `seen` array that is not the authenticated user's own `profileId`.
3. IF the `MESSAGE_SEEN` event payload is absent or cannot be parsed as a list of message IDs, THEN THE Chat_Panel SHALL log the error and SHALL NOT modify any seen indicators.

---

### Requirement 9: Handling MESSAGE_REACTION Events

**User Story:** As a user, I want to see emoji reactions added by other participants update live on the relevant message, so that I have an accurate view of message reactions.

#### Acceptance Criteria

1. WHEN the WebSocket_Service delivers a `ConversationEvent` with `eventType` = `MESSAGE_REACTION` to a Chat_Panel, THE Chat_Panel SHALL replace the displayed `Message` whose `id` matches the updated `Message` in the event payload with the updated `Message`.
2. THE Chat_Panel SHALL display each distinct reaction string from a message's `reactions` array alongside the count of how many entries share that reaction string; if a message has no reactions, the reaction section SHALL NOT be rendered.
3. IF the `MESSAGE_REACTION` event payload is absent or cannot be matched to an existing displayed message by `id`, THEN THE Chat_Panel SHALL log the error and SHALL NOT modify the displayed message list.

---

### Requirement 10: Handling MESSAGE_EDIT Events

**User Story:** As a user, I want edited messages to update live in the conversation panel, so that I always see the current content of a message.

#### Acceptance Criteria

1. WHEN the WebSocket_Service delivers a `ConversationEvent` with `eventType` = `MESSAGE_EDIT` to a Chat_Panel, THE Chat_Panel SHALL replace the displayed `Message` whose `id` matches the updated `Message` in the event payload with the updated `Message`.
2. THE Chat_Panel SHALL display an "edited" indicator on any message whose `versions` array contains more than one entry.
3. IF the `MESSAGE_EDIT` event payload is absent or cannot be matched to an existing displayed message by `id`, THEN THE Chat_Panel SHALL log the error and SHALL NOT modify the displayed message list.

---

### Requirement 11: Sending Messages

**User Story:** As a user, I want to type and send a plain-text message in a conversation panel, so that I can participate in the conversation.

#### Acceptance Criteria

1. THE Chat_Panel SHALL provide a text input field and a send button for composing messages.
2. WHEN the user submits a message (by clicking the send button or pressing Enter with the text input focused), THE Messaging_Service SHALL call `POST /Messages?conversationId={id}` with the trimmed plain-text message body as the request body (`Content-Type: text/plain`).
3. IF the `POST /Messages` request succeeds, THEN THE Chat_Panel SHALL clear the text input field.
4. IF the `POST /Messages` request fails, THEN THE Chat_Panel SHALL display an inline error message and retain the composed text so the user can retry.
5. WHILE a send request is in-flight, THE Chat_Panel SHALL disable the send button and text input to prevent duplicate submissions.
6. WHEN the user attempts to submit a message whose trimmed content is empty, THE Chat_Panel SHALL NOT call `POST /Messages` and SHALL display a validation message; the text input SHALL retain focus.

---

### Requirement 12: Marking Messages as Seen

**User Story:** As a user, I want messages I have viewed to be automatically marked as seen, so that other participants know I have read their messages.

#### Acceptance Criteria

1. WHEN a Chat_Panel becomes the Focused_Panel and it displays at least one `Message` whose `seen` array does not include the authenticated user's `profileId`, THEN THE Messaging_Service SHALL call `PATCH /Messages/seen` with the IDs of all such messages in a single request.
2. WHEN a `NEW_MESSAGE` event is received and the receiving Chat_Panel is currently the Focused_Panel, THE Chat_Panel SHALL include the new message's `id` in the next `PATCH /Messages/seen` call; if no other unseen messages are pending, THE Messaging_Service SHALL send the `PATCH` immediately.
3. IF a `PATCH /Messages/seen` request fails, THEN THE Chat_Panel SHALL log the error; the local seen indicators SHALL NOT be updated until a successful response is received.

---

### Requirement 13: Reacting to Messages

**User Story:** As a user, I want to react to individual messages with an emoji, so that I can respond quickly without writing a full reply.

#### Acceptance Criteria

1. THE Chat_Panel SHALL display a reaction affordance (e.g., an emoji picker button) on each message that is activated by hovering over or tapping the message.
2. WHEN the user selects a reaction from the affordance, THE Messaging_Service SHALL call `PATCH /Messages/react?messageId={id}` with the selected emoji as a plain-text request body (`Content-Type: text/plain`).
3. WHILE the `PATCH /Messages/react` request is in-flight, THE Chat_Panel SHALL disable the reaction affordance for that message to prevent duplicate submissions.
4. IF the `PATCH /Messages/react` request fails, THEN THE Chat_Panel SHALL re-enable the reaction affordance, display an inline error on the affected message, and leave the message's existing reactions unchanged.

---

### Requirement 14: Editing Messages

**User Story:** As the author of a message, I want to be able to edit its content after sending, so that I can correct mistakes.

#### Acceptance Criteria

1. THE Chat_Panel SHALL display an edit affordance exclusively on messages whose `authorId` matches the authenticated user's `id`.
2. WHEN the user activates the edit affordance, THE Chat_Panel SHALL replace the message's display with an editable text field pre-filled with the text from the most recent entry in the message's `versions` array.
3. WHEN the user confirms an edit and the edited text (trimmed) is non-empty, THE Messaging_Service SHALL call `PUT /Messages?messageId={id}` with the updated plain-text body.
4. IF the user confirms an edit with an empty or whitespace-only body, THEN THE Chat_Panel SHALL display a validation error within the edit field and SHALL NOT call `PUT /Messages`.
5. IF the `PUT /Messages` request succeeds, THEN THE Chat_Panel SHALL exit edit mode and display the updated message.
6. IF the `PUT /Messages` request fails, THEN THE Chat_Panel SHALL display an inline error message within the edit field, remain in edit mode, and retain the user's edited draft so they can retry.
7. WHEN the user cancels an edit, THE Chat_Panel SHALL exit edit mode and restore the original message display without calling the API.

---

### Requirement 15: Theming and Style

**User Story:** As a user, I want the messaging app to respect my saved style and dark mode preference, so that the UI is visually consistent with other TrecApps applications.

#### Acceptance Criteria

1. THE App SHALL apply the CSS class from `StylesService.style` signal to the root host element, and SHALL update that class whenever the signal changes.
2. WHEN `AuthService.onLoginSuccess()` is called, THE App SHALL read `AccountList.mainUserAccount.extensions.styles` and apply style preferences by calling `StylesService.setStyle()` and `StylesService.setDarkMode()` with the resolved values.
3. IF `AccountList.mainUserAccount.extensions.styles[environment.app_name]` is absent, THEN THE App SHALL use `AccountList.mainUserAccount.extensions.styles.main` as the fallback.
4. IF neither the app-specific nor the `main` style preference is present, THEN THE App SHALL call `StylesService.setStyle("default")` and `StylesService.setDarkMode(false)`.
5. IF the resolved style name is not a valid entry in `StylesService.getAvailableStyles()`, THEN THE App SHALL fall back to `"default"` rather than applying an invalid style.
