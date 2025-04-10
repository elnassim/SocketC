CREATE DATABASE IF NOT EXISTS chat_app;
USE chat_app;

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(255) PRIMARY KEY, -- Using your existing ID format
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_group BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(255) PRIMARY KEY, -- Using your existing ID format
    sender_email VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL, -- private, broadcast, etc.
    status VARCHAR(20) NOT NULL, -- SENT, DELIVERED, READ
    timestamp BIGINT NOT NULL,
    delivered BOOLEAN DEFAULT FALSE,
    read_status BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (sender_email) REFERENCES users(email)
);

CREATE TABLE IF NOT EXISTS conversation_participants (
    conversation_id VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_email),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (user_email) REFERENCES users(email)
);

CREATE TABLE IF NOT EXISTS contacts (
    user_email VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_email, contact_email),
    FOREIGN KEY (user_email) REFERENCES users(email),
    FOREIGN KEY (contact_email) REFERENCES users(email)
);

-- Add these to your schema.sql file if not already there
CREATE TABLE IF NOT EXISTS user_groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS group_members (
    group_id INT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_email),
    FOREIGN KEY (group_id) REFERENCES user_groups(id),
    FOREIGN KEY (user_email) REFERENCES users(email)
);

-- Table to store file metadata
CREATE TABLE IF NOT EXISTS files (
    id VARCHAR(36) PRIMARY KEY,  -- UUID for the file
    sender_email VARCHAR(255) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,  -- Original name of the file
    stored_path VARCHAR(255) NOT NULL,        -- Server-side storage path
    mime_type VARCHAR(100) NOT NULL,          -- File type (image/jpeg, application/pdf, etc.)
    file_size BIGINT NOT NULL,                -- Size in bytes
    timestamp BIGINT NOT NULL,                -- When the file was sent
    delivered BOOLEAN DEFAULT FALSE,
    viewed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (sender_email) REFERENCES users(email)
);