# EV Charge Book Android

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- Navigation Compose
- Retrofit
- MVVM Architecture

## Package Design

```
app
├── ui
│   ├── dashboard
│   ├── charging
│   ├── vehicle
│   └── settings
│
├── data
│   ├── entity
│   ├── dao
│   └── repository
│
├── domain
│   ├── model
│   └── usecase
│
└── database
```

## MVP Screens

1. Dashboard
2. Add Charging Record
3. Charging History
4. Vehicle Profile

## Development Goal

First version focuses on local storage.

Cloud synchronization, AI analysis and IoT integration will be introduced later.
