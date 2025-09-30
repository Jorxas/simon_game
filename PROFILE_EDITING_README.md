# Profile Editing Functionality

## Overview
This implementation adds a comprehensive profile editing system that allows users to modify their password and select from 10 predefined profile pictures.

## Features

### 1. Password Management
- **Current Password Required**: Users must provide their current password to make any changes
- **Optional New Password**: Users can optionally change their password
- **Password Validation**: New passwords must be at least 6 characters long
- **Password Confirmation**: New passwords must be confirmed to prevent typos

### 2. Profile Picture Selection
- **10 Predefined Pictures**: Users can choose from 10 different SVG profile pictures
- **Visual Selection**: Click on any picture to select it with visual feedback
- **Default Selection**: If no picture is currently set, the first picture is selected by default
- **Current Picture Highlighting**: The user's current profile picture is highlighted when the modal opens

### 3. User Interface
- **Modal Interface**: Clean, responsive modal dialog for editing
- **Grid Layout**: Profile pictures are displayed in a 4-column grid
- **Visual Feedback**: Selected pictures have a blue border and shadow effect
- **Hover Effects**: Pictures scale up slightly on hover for better UX

## Technical Implementation

### Backend Changes

#### ProfilController.java
- Added `/api/profile/pictures` endpoint to return predefined profile picture URLs
- Updated profile update endpoint to handle password changes and profile picture selection

#### ProfilService.java
- Added validation for predefined profile picture URLs
- Enhanced password validation logic
- Added `isValidProfilePicture()` method to validate picture URLs

#### ProfilRepository.java
- Updated database queries to handle optional password updates
- Maintains existing profile picture URL in database

### Frontend Changes

#### lobby.html
- Updated edit profile modal with password fields
- Replaced URL input with profile picture grid
- Added proper form validation

#### lobby.js
- Added `loadProfilePictures()` function to fetch and display predefined pictures
- Updated `savePlayerProfile()` to handle password changes and picture selection
- Enhanced `openEditProfileModal()` to load current profile picture
- Added click handlers for profile picture selection

#### lobby.css
- Added styles for profile picture grid and selection
- Implemented hover and selection effects
- Added dark theme support for profile pictures

### Profile Pictures
- **Format**: SVG files for scalability and small file size
- **Location**: `/frontend/images/profiles/`
- **Naming**: `profile1.svg` through `profile10.svg`
- **Design**: Simple avatar designs with different color schemes

## Usage

1. **Access Edit Profile**: Click the "Edit" button next to any player in the lobby table
2. **Enter Current Password**: Required to make any changes
3. **Optional Password Change**: 
   - Leave new password fields empty to keep current password
   - Enter new password and confirmation if changing
4. **Select Profile Picture**: Click on any of the 10 predefined pictures
5. **Save Changes**: Click "Save Changes" to update the profile

## API Endpoints

### GET /api/profile/pictures
Returns the list of predefined profile picture URLs.

**Response:**
```json
{
  "success": true,
  "pictures": {
    "1": "/images/profiles/profile1.svg",
    "2": "/images/profiles/profile2.svg",
    ...
    "10": "/images/profiles/profile10.svg"
  }
}
```

### PUT /api/profile
Updates user profile with new password and/or profile picture.

**Request Body:**
```json
{
  "currentUsername": "username",
  "currentPassword": "current_password",
  "newUsername": "new_username",
  "newPassword": "new_password", // optional
  "newProfileUrl": "/images/profiles/profile1.svg" // optional
}
```

**Response:**
```json
{
  "message": "Profile updated successfully",
  "user": {
    "username": "new_username",
    "userId": 123,
    "profileUrl": "/images/profiles/profile1.svg"
  }
}
```

## Security Features

1. **Current Password Verification**: All changes require current password verification
2. **Input Validation**: Server-side validation of all inputs
3. **Predefined Pictures Only**: Only predefined profile picture URLs are accepted
4. **Password Strength**: Minimum 6 characters for new passwords
5. **Username Uniqueness**: Prevents duplicate usernames

## Error Handling

- Invalid current password
- Username already exists
- Invalid profile picture URL
- Password confirmation mismatch
- Password too short
- Missing required fields

All errors are displayed to the user via toast notifications.
