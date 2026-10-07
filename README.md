# Smart OSM (Public Health Volunteer Management)

Modern Android application for Population Registration, Household Management, and NCDs Health Screening.

## Features
- **Identity First:** Secure identity management for Households and Persons using UUIDs.
- **Offline First:** Full functionality without internet using Room Database.
- **Secure Sync:** Bi-directional sync with Cloud Firestore.
- **Advanced GIS:** 100m Epidemic Buffer and Optimized Visit Routes.
- **Health AI:** Local AI-powered health advice for NCDs screening.
- **Privacy:** Database encryption (SQLCipher), PIN Lock with Lockout, and data anonymization for AI.

## Security
- Data encrypted at rest (SQLCipher).
- PIN hashing and lockout mechanism.
- Android Keystore for sensitive credentials.
- Strict Firestore security rules (Village-level isolation).

## License
MIT License - Copyright (c) 2024
