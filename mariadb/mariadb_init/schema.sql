
CREATE TABLE players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    profileUrl VARCHAR(500) DEFAULT NULL
    
);

-- Table pour gérer les lobbies de jeu
CREATE TABLE lobbies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('waiting', 'playing', 'finished', 'cancelled') DEFAULT 'waiting',
    current_players INT NOT NULL DEFAULT 0,
    round_duration INT NOT NULL DEFAULT 30, -- en secondes
    speed_multiplier DECIMAL(2,1) NOT NULL DEFAULT 1.0,
    sequence_min_length INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL
   
);


-- Table pour les contrôleurs
CREATE TABLE controllers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('online', 'busy') DEFAULT 'online'
);

-- Table de liaison entre lobbies et joueurs
CREATE TABLE lobbies_players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    lobby_id INT NOT NULL,
    player_id INT NOT NULL,
    controller_id INT,
    role ENUM('player', 'spectator') DEFAULT 'player',
    status ENUM('waiting', 'ready', 'playing') DEFAULT 'waiting',
    score INT DEFAULT 0,
    round INT DEFAULT 0,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (lobby_id) REFERENCES lobbies(id) ON DELETE CASCADE,
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
    FOREIGN KEY (controller_id) REFERENCES controllers(id) ON DELETE SET NULL,
    UNIQUE KEY unique_lobby_player (lobby_id, player_id)
);

