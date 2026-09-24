# DENO — Complete Frontend APK (Phase 1)

**App Name:** DENO  
**Package:** com.deno.social  
**Tagline:** Learn · Share · Inspire

## Phase 1 Status

Frontend ONLY. Backend is intentionally NOT connected.

- No Firebase
- No google-services.json
- No real auth / database / storage
- All data is local mock data

## Screens Included

- Splash
- Login (with Google G button only)
- Signup
- Forgot Password
- Google Complete Profile
- Home (+ Create button, feed, like/comment)
- Create Menu (bottom sheet)
- Create Post
- Create Reel
- Reels Feed (vertical swipe)
- Search + Search Results
- Own Profile / Other User Profile
- Edit Profile
- Comments
- Notifications
- Settings (Dark Mode toggle)
- Privacy / Terms / About / Help

## Bottom Navigation

Home · Search · **+** · Reels · Profile

## Build

```bash
./gradlew assembleDebug
```

APK path after build:
`app/build/outputs/apk/debug/app-debug.apk`

## Tech

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Mock repositories (ready for Firebase later)

## Architecture

Interfaces + Mock implementations:
- UserRepository / MockUserRepository
- PostRepository / MockPostRepository
- ReelRepository / MockReelRepository
- CommentRepository / MockCommentRepository
- NotificationRepository / MockNotificationRepository

Later swap mocks for Firebase without rewriting UI.
