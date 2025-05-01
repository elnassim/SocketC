# Chat Application - Authentication Guide

This document explains the authentication functionalities (Login, Signup, and Logout) of the chat application.

## Table of Contents
- [Login](#login)
- [Signup](#signup)
- [Logout](#logout)
- [Requirements](#requirements)

## Login

The login functionality allows existing users to access their chat account.

### Features
- Email and password authentication
- Client-side email format validation
- Secure socket connection to server
- Automatic redirection to chat interface upon successful login

### How to Login
1. Launch the application
2. Enter your registered email address
3. Enter your password
4. Click the "Connect" button
5. Wait for server authentication

### Error Handling
- Empty fields validation
- Invalid email format detection
- Incorrect credentials handling
- Network connection errors

## Signup

The signup functionality allows new users to create an account.

### Features
- Username, email, and password registration
- Password confirmation
- Client-side input validation
- Duplicate email checking

### Registration Requirements
- Valid email address format
- Password minimum length: 6 characters
- Matching password confirmation
- Unique email address (not already registered)

### How to Sign Up
1. From the login screen, click "Sign Up"
2. Fill in the registration form:
   - Username
   - Email address
   - Password
   - Confirm password
3. Click "Register"
4. Upon successful registration, you'll be redirected to the login screen

### Validation Rules
- All fields are required
- Email must be in valid format
- Password must be at least 6 characters
- Passwords must match
- Email must not be already registered

## Logout

The logout functionality allows users to securely end their chat session.

### Features
- Secure socket connection closure
- Session cleanup
- Automatic redirection to login screen

### How to Logout
1. Click the logout button in the chat interface
2. The application will:
   - Close the socket connection
   - Clear session data
   - Return to the login screen

### Security Measures
- Proper socket connection closure
- Session data cleanup
- Automatic login screen redirection

## Requirements

### System Requirements
- Java Runtime Environment (JRE) 8 or higher
- Active internet connection
- Minimum 512MB RAM
- 100MB free disk space

### Network Requirements
- Open port 1234 for socket connection
- Stable internet connection
- Server accessibility

### Dependencies
- JavaFX for UI
- JSON for data exchange
- Socket programming for client-server communication 