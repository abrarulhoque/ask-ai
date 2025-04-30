# AskAI - English Language Learning Assistant

AskAI is an Android application designed to help non-native English speakers understand vocabulary and expressions. The app allows users to select text from any application and get clear, simple explanations, making English learning more accessible.

## Features

- **Text Selection Integration**: Select any text in any app and use AskAI for definitions
- **Simple Explanations**: Get clear, easy-to-understand definitions tailored for language learners
- **History Tracking**: Save all your previous definitions for future reference
- **Configurable Settings**: Customize the API key, model, and system prompt
- **Overlay Display**: View definitions in a floating window without leaving your current app

## Implementation Details

- Uses OpenAI API for generating definitions
- Built with Jetpack Compose for modern UI
- Uses DataStore for persistent storage
- Features a settings page for customization
- Implements Android's Process Text API for seamless integration

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Build and run the app
4. Configure your OpenAI API key in the settings screen of the app

### API Key Configuration

For security reasons, the app does not include an OpenAI API key by default. You need to:

1. Obtain an API key from [OpenAI's platform](https://platform.openai.com)
2. Enter your API key in the app's settings screen
3. The app will save your API key securely using Android's DataStore

Alternatively, for development purposes, you can modify the `buildConfigField` in `app/build.gradle.kts` to include your API key.

## Technical Stack

- Kotlin
- Jetpack Compose
- DataStore
- MVVM Architecture
- OpenAI API Client

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- OpenAI for their powerful language models
- Android documentation for Process Text API
