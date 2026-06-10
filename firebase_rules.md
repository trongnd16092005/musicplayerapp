# Firebase Rules

Use these rules after enabling Firebase Authentication, Realtime Database, and Storage.

## Realtime Database

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "songs": {
      ".read": "auth != null",
      "$songId": {
        ".write": "auth != null && (!data.exists() || data.child('ownerUid').val() == auth.uid) && (!newData.exists() || newData.child('ownerUid').val() == auth.uid)"
      }
    },
    "userLibraries": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    }
  }
}
```

## Storage

```txt
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /users/{uid}/{folder}/{fileName} {
      allow read: if request.auth != null;
      allow write, delete: if request.auth != null && request.auth.uid == uid;
    }
  }
}
```
