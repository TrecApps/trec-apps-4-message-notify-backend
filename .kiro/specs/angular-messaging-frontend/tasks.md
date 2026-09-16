# Implementation Plan: Angular Messaging Frontend

## Overview

Build a standalone Angular 21 messaging frontend application in the `frontend-base` folder. The implementation proceeds in layers: project scaffolding and shared data models first, then services (HTTP and WebSocket), then routing/auth, then the conversation list page, and finally the chat panel with all real-time event handling. Each layer is tested incrementally before wiring to the next.

## Tasks

- [x] 1. Scaffold Angular project and define shared data models
  - [x] 1.1 Initialise the Angular 21 standalone application in `frontend-base` and install dependencies (`@stomp/stompjs`, `fast-check`)
    - Run `ng new frontend-base --standalone --routing --style=css` (or equivalent) inside `frontend-base`
    - Add `@stomp/stompjs` and `fast-check` as exact-version dependencies
    - Copy `environment.ts`, `account.ts`, `auth-service.ts`, `styles-service.ts`, `standard.ts` from `mg-helper-files` into the new project
    - _Requirements: 1.1, 5.1, 15.1_
  - [x] 1.2 Create shared TypeScript interfaces and model types
    - Create `src/app/models/models.ts` with `Conversation`, `ConversationMarker`, `Message`, `MessageVersion`, `Reaction`, `ConversationEvent`, `EventType`, `WebSocketConnectionStatus`, `PanelEntry`, `EditState`
    - Derive `latestActivity` helper that picks the `messageMade` of the last `markers` entry
    - Create `aggregateReactions` utility function
    - _Requirements: 7.1, 9.2, 10.2_

- [x] 2. Implement `MessagingService`
  - [x] 2.1 Create `MessagingService` with all REST methods
    - Create `src/app/services/messaging.service.ts`
    - Implement `getConversations()`, `createConversation()`, `getMessages()`, `getLatestMessages()`, `sendMessage()`, `markSeen()`, `reactToMessage()`, `editMessage()` using `HttpClient` with `withCredentials: true`
    - Derive `latestActivity` on conversations inside `getConversations()` from `markers`
    - All methods return `Observable<T>`
    - _Requirements: 2.1, 4.1, 4.4, 11.2, 12.1, 13.2, 14.3_
  - [ ]* 2.2 Write unit tests for `MessagingService`
    - Create `src/app/services/messaging.service.spec.ts`
    - Use `HttpClientTestingModule` and `HttpTestingController`
    - Verify correct HTTP verbs, URLs, query params, headers, and body serialization for each method
    - _Requirements: 2.1, 11.2, 12.1, 13.2, 14.3_
  - [ ]* 2.3 Write property test for `MessagingService` — send trims whitespace (Property 15)
    - **Property 15: Send trims whitespace before posting**
    - **Validates: Requirements 11.2, 11.6**

- [x] 3. Implement `RelativeTimePipe`
  - [x] 3.1 Create `RelativeTimePipe` standalone pipe
    - Create `src/app/pipes/relative-time.pipe.ts`
    - Implement `transform(value: string | null | undefined): string` using `Date` arithmetic to produce human-readable relative strings ("just now", "2 minutes ago", "3 hours ago", etc.)
    - Handle `null`/`undefined` inputs gracefully
    - _Requirements: 2.6_
  - [ ]* 3.2 Write property test for `RelativeTimePipe` (Property 3)
    - **Property 3: RelativeTimePipe produces a non-empty relative string**
    - **Validates: Requirements 2.6**

- [x] 4. Implement `PanelManagerService`
  - [x] 4.1 Create `PanelManagerService` with open/close/focus/evict logic
    - Create `src/app/services/panel-manager.service.ts`
    - Implement `openPanels: WritableSignal<PanelEntry[]>` (max 3)
    - Implement `openPanel(conversation)` with deduplication, LRU eviction on overflow, and focus-move
    - Implement `closePanel(conversationId)`, `focusPanel(conversationId)`, `updateLastInteraction(conversationId)`
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6_
  - [ ]* 4.2 Write property test for `PanelManagerService` — LRU eviction (Property 4)
    - **Property 4: LRU eviction selects the panel with the oldest last-interaction time**
    - **Validates: Requirements 3.3**

- [x] 5. Implement `WebSocketService`
  - [x] 5.1 Create `WebSocketService` with STOMP connection management
    - Create `src/app/services/websocket.service.ts`
    - Instantiate a single `@stomp/stompjs` `Client` pointed at `${environment.message_service_url}/ws`
    - Implement `connect()` / `disconnect()` called by router events (navigate to/from `/home`) and by `AuthService` logout
    - Expose `connectionStatus$: Observable<WebSocketConnectionStatus>`
    - _Requirements: 5.1, 5.2, 5.4, 5.5, 5.6_
  - [x] 5.2 Implement exponential backoff reconnect logic in `WebSocketService`
    - Apply `delay(attempt) = min(5000 × 2^(attempt−1), 60000)` ms formula
    - Stop after 10 consecutive failed attempts and emit `FAILED` status
    - Apply same backoff for both initial connection failure and mid-session drops
    - _Requirements: 5.3, 5.7_
  - [x] 5.3 Implement subscription queue and `eventsFor()` in `WebSocketService`
    - Implement `subscribe(conversationId)` / `unsubscribe(conversationId)` with pending queue (max 50, 30 s timeout)
    - Drain queue on connection established; discard queue and notify panels on overflow or timeout
    - Expose `eventsFor(conversationId: string): Observable<ConversationEvent>` via `Subject`-based multicast
    - Handle STOMP ERROR frame on subscribe
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  - [ ]* 5.4 Write property tests for `WebSocketService` — backoff, round-trip, subscription queue (Properties 5, 6, 7)
    - **Property 5: Exponential backoff delay formula**
    - **Validates: Requirements 5.3, 5.7**
    - **Property 6: ConversationEvent deserialization round-trip**
    - **Validates: Requirements 6.3**
    - **Property 7: Subscription queue — all pending requests sent after connection**
    - **Validates: Requirements 6.4**
  - [ ]* 5.5 Write unit tests for `WebSocketService`
    - Mock `@stomp/stompjs` `Client` to avoid real WebSocket connections
    - Test: connects to correct URL on `/home` navigation; disconnects on navigate-away and logout; single connection shared across panels
    - _Requirements: 5.1, 5.4, 5.5, 5.6_

- [x] 6. Checkpoint — Ensure all service tests pass
  - Ensure all service-layer tests pass, ask the user if questions arise.

- [x] 7. Implement routing, `AuthGuard`, and `AppComponent`
  - [x] 7.1 Create `authGuard` functional route guard
    - Create `src/app/guards/auth.guard.ts`
    - Read `AuthService.account()` signal; redirect to `/logon` if `undefined`, allow otherwise
    - _Requirements: 1.7_
  - [ ]* 7.2 Write unit tests for `authGuard`
    - Test: allows authenticated users through; redirects unauthenticated users to `/logon`
    - _Requirements: 1.7_
  - [x] 7.3 Create `AppComponent` with style binding and auth bootstrap
    - Create `src/app/app.component.ts` / `.html` / `.css`
    - Bind `[class]` to `StylesService.style` signal on `:host`; call `StylesService.setStyle()` and `StylesService.setDarkMode()` on login success with app-specific/main/default fallback logic
    - Call `AuthService.attemptRefresh()` on `ngOnInit`; manage `loading` signal to gate rendering; display `<app-splash-screen>` while loading
    - Render `<router-outlet>` and `<app-chat-panel-host>` when not loading
    - Configure app routes: `/logon` → `LogonPageComponent`, `/home` → `ConversationListPageComponent` (with `authGuard`), `**` → redirect to `/logon`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 15.1, 15.2, 15.3, 15.4, 15.5_
  - [ ]* 7.4 Write unit tests for `AppComponent`
    - Test: splash shown during in-flight refresh; navigates to `/home` on success; navigates to `/logon` on failure
    - _Requirements: 1.2, 1.3, 1.4_
  - [ ]* 7.5 Write property test for `AppComponent` — root host CSS class binding (Property 20)
    - **Property 20: Root host CSS class matches StylesService.style signal**
    - **Validates: Requirements 15.1**

- [x] 8. Implement `LogonPageComponent`
  - [x] 8.1 Create `LogonPageComponent` wrapping the provided `LoginComponent`
    - Create `src/app/pages/logon/logon-page.component.ts` / `.html`
    - Import and render the provided `LoginComponent` directly; no additional logic
    - _Requirements: 1.5_

- [x] 9. Implement `ConversationListPageComponent`
  - [x] 9.1 Create `ConversationListPageComponent` with conversation fetching and display
    - Create `src/app/pages/conversation-list/conversation-list.component.ts` / `.html` / `.css`
    - Call `MessagingService.getConversations()` on `ngOnInit`; manage `conversations`, `loadingConvs`, `convError` signals
    - Sort by `latestActivity` descending (nulls last) before rendering
    - Display conversation ID, participant count (`profiles.length`), and `latestActivity` via `RelativeTimePipe`
    - Show loading indicator while loading; show error message and retry button on failure
    - On conversation row click: call `PanelManagerService.openPanel(conversation)`
    - Subscribe to `WebSocketService.conversationUpdates$` to update `latestActivity` and unread counts for conversations without an open panel
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 7.6_
  - [ ]* 9.2 Write unit tests for `ConversationListPageComponent`
    - Test: `GET /Conversations` called on init; loading indicator shown; error and retry on failure; `latestActivity` displayed for conversations with markers
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_
  - [ ]* 9.3 Write property tests for `ConversationListPageComponent` — one entry per conversation, sort order, NEW_MESSAGE closed panel update (Properties 1, 2, 9)
    - **Property 1: Conversation list renders one entry per conversation**
    - **Validates: Requirements 2.2**
    - **Property 2: Conversation sort order — latestActivity descending, nulls last**
    - **Validates: Requirements 2.5**
    - **Property 9: NEW_MESSAGE for closed panel updates conversation list**
    - **Validates: Requirements 7.6**

- [x] 10. Implement `ChatPanelHostComponent`
  - [x] 10.1 Create `ChatPanelHostComponent` as a fixed-position panel container
    - Create `src/app/components/chat-panel-host/chat-panel-host.component.ts` / `.html` / `.css`
    - Use `PanelManagerService.openPanels` signal to render up to 3 `ChatPanelComponent` instances
    - Layout: `position: fixed; bottom: 0; right: 0` with flex row; each panel 320 px wide; rightmost = most recently focused
    - _Requirements: 3.1, 3.2, 3.6_

- [x] 11. Implement `ChatPanelComponent` — core structure and message loading
  - [x] 11.1 Create `ChatPanelComponent` scaffold with message history loading
    - Create `src/app/components/chat-panel/chat-panel.component.ts` / `.html` / `.css`
    - Accept `@Input() conversation: Conversation`
    - On `ngOnInit`: call `MessagingService.getMessages(conversationId, 0)` and `WebSocketService.subscribe(conversationId)`; set `lowestLoadedPage` to 0
    - On `ngOnDestroy`: call `WebSocketService.unsubscribe(conversationId)`
    - Manage `messages`, `loadingMessages`, `loadingMore`, `sendError`, `sendPending`, `editState`, `unreadBadgeCount`, `isFocused`, `lowestLoadedPage` signals
    - Display messages in chronological order; show loading indicator during initial load; hide further-page trigger when `lowestLoadedPage === 0`
    - Implement pagination scroll trigger at top: call `getMessages(conversationId, lowestLoadedPage - 1)` and prepend results; show top loading indicator while loading
    - Track `lastInteractionTime` and call `PanelManagerService.updateLastInteraction()` on click, scroll, and keydown
    - Implement close button to call `PanelManagerService.closePanel(conversationId)` and `WebSocketService.unsubscribe(conversationId)`
    - Display header with visual distinction when `isFocused` is true
    - _Requirements: 3.4, 3.6, 3.7, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  - [ ]* 11.2 Write unit tests for `ChatPanelComponent` initial load
    - Test: `GET /Messages` page=0 called on open; loading indicator shown; pagination trigger hidden at page 0
    - _Requirements: 4.1, 4.2, 4.6_

- [x] 12. Implement `ChatPanelComponent` — send, edit, react, and mark-seen
  - [x] 12.1 Implement send message functionality in `ChatPanelComponent`
    - Add text input and send button to the panel template
    - On submit (button click or Enter): trim text; if empty, show validation message and retain focus; if non-empty, call `MessagingService.sendMessage()`, disable input/button while in-flight, clear input on success, show inline error and retain draft on failure
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_
  - [x] 12.2 Implement mark-seen logic in `ChatPanelComponent`
    - When panel gains focus, call `MessagingService.markSeen()` with all message IDs not in the authenticated user's seen state, in a single request
    - Log errors; do not update local seen indicators until successful response
    - _Requirements: 12.1, 12.2, 12.3_
  - [x] 12.3 Implement reaction affordance in `ChatPanelComponent`
    - Display emoji picker affordance on each message (hover/tap to activate)
    - On emoji selection, call `MessagingService.reactToMessage(messageId, emoji)`; disable affordance while in-flight; re-enable and show inline error on failure
    - Display aggregated reaction counts using `aggregateReactions`; hide reaction section if empty
    - _Requirements: 9.2, 13.1, 13.2, 13.3, 13.4_
  - [x] 12.4 Implement edit affordance and edit mode in `ChatPanelComponent`
    - Show edit affordance only on messages where `message.profile === authService.currentAccountId`
    - On edit activation, switch to edit mode with text field pre-filled from last `messageVersions` entry
    - On confirm: trim; if empty show validation error; if non-empty call `MessagingService.editMessage()`; exit edit mode on success; stay in edit mode with inline error on failure
    - On cancel: exit edit mode without API call
    - Display "edited" indicator when `messageVersions.length > 1`
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 10.2_
  - [ ]* 12.5 Write unit tests for `ChatPanelComponent` send, edit, react, and mark-seen
    - Test: send button disabled while in-flight; edit mode toggled correctly; cancel edit does not call API
    - _Requirements: 11.5, 14.5, 14.7_

- [x] 13. Implement `ChatPanelComponent` — WebSocket event handlers
  - [x] 13.1 Wire `WebSocketService.eventsFor()` in `ChatPanelComponent` and handle `NEW_MESSAGE`
    - Subscribe to `WebSocketService.eventsFor(conversationId)` and dispatch events
    - `NEW_MESSAGE`: append valid `Message` to end of `messages`; if scroll within 100 px of bottom, auto-scroll; otherwise increment `unreadBadgeCount`; on badge click, scroll to bottom and reset count; log and skip invalid payloads
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  - [x] 13.2 Handle `MESSAGE_SEEN`, `MESSAGE_REACTION`, and `MESSAGE_EDIT` events in `ChatPanelComponent`
    - `MESSAGE_SEEN`: update seen indicator on each message whose `id` appears in payload; show read receipt when non-self participant has seen the message; log and skip invalid payloads
    - `MESSAGE_REACTION`: replace matching message in list with updated `Message` from payload; log and skip if missing/unmatched
    - `MESSAGE_EDIT`: replace matching message in list with updated `Message` from payload; log and skip if missing/unmatched
    - _Requirements: 8.1, 8.2, 8.3, 9.1, 9.3, 10.1, 10.3_
  - [ ]* 13.3 Write property tests for `ChatPanelComponent` — event handling (Properties 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
    - **Property 8: NEW_MESSAGE appends to end of message list**
    - **Validates: Requirements 7.1**
    - **Property 10: MESSAGE_SEEN updates all matching seen indicators**
    - **Validates: Requirements 8.1**
    - **Property 11: Read receipt shown iff non-self participant has seen the message**
    - **Validates: Requirements 8.2**
    - **Property 12: MESSAGE_REACTION and MESSAGE_EDIT replace the matching message**
    - **Validates: Requirements 9.1, 10.1**
    - **Property 13: Reaction aggregation renders each distinct emoji with correct count**
    - **Validates: Requirements 9.2**
    - **Property 14: Edited indicator shown iff message has more than one version**
    - **Validates: Requirements 10.2**
    - **Property 15: Send trims whitespace before posting**
    - **Validates: Requirements 11.2, 11.6**
    - **Property 16: Mark-seen sends all unseen message IDs in a single request**
    - **Validates: Requirements 12.1**
    - **Property 17: Reaction API called with correct messageId and emoji**
    - **Validates: Requirements 13.2**
    - **Property 18: Edit affordance visible only for messages authored by current user**
    - **Validates: Requirements 14.1**
    - **Property 19: Edit confirmation posts trimmed text; whitespace-only body rejected**
    - **Validates: Requirements 14.3, 14.4**

- [x] 14. Checkpoint — Ensure all component and service tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Wire application together and apply theming
  - [x] 15.1 Register all components and services in the application bootstrap
    - Update `src/main.ts` and `app.config.ts` to bootstrap `AppComponent` with `provideRouter`, `provideHttpClient(withFetch())`, and all application providers
    - Import `ChatPanelHostComponent` and `ConversationListPageComponent` into `AppComponent`
    - _Requirements: 1.5, 1.6, 2.1, 3.1_
  - [x] 15.2 Apply theming and `StylesService` integration
    - Ensure `AppComponent` correctly reads `AccountList.mainUserAccount.extensions.styles` on login success, applies app-specific/main/default fallbacks, and validates against `StylesService.getAvailableStyles()`
    - Write CSS to visually distinguish the focused `ChatPanelComponent` (e.g., elevated border or highlighted header)
    - _Requirements: 3.6, 15.1, 15.2, 15.3, 15.4, 15.5_
  - [ ]* 15.3 Write integration tests for end-to-end panel flow
    - Test: open panel from conversation list; panel subscribes to WebSocket; close panel unsubscribes; 4th panel evicts LRU
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.1, 6.2_

- [x] 16. Final checkpoint — Ensure all tests pass
  - Run the full test suite; ensure all tests pass. Ask the user if any questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using `fast-check`
- Unit tests validate specific interactions, loading states, error paths, and lifecycle hooks
- The design uses TypeScript/Angular 21 standalone components throughout

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "3.1", "4.1", "8.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.2", "4.2", "5.1"] },
    { "id": 3, "tasks": ["5.2", "7.1"] },
    { "id": 4, "tasks": ["5.3", "7.3"] },
    { "id": 5, "tasks": ["5.4", "5.5", "7.2", "7.4", "7.5", "10.1"] },
    { "id": 6, "tasks": ["9.1"] },
    { "id": 7, "tasks": ["9.2", "9.3", "11.1"] },
    { "id": 8, "tasks": ["11.2", "12.1", "12.2", "12.3", "12.4"] },
    { "id": 9, "tasks": ["12.5", "13.1"] },
    { "id": 10, "tasks": ["13.2"] },
    { "id": 11, "tasks": ["13.3", "15.1"] },
    { "id": 12, "tasks": ["15.2"] },
    { "id": 13, "tasks": ["15.3"] }
  ]
}
```
