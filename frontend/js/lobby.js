// Initialize page
document.addEventListener('DOMContentLoaded', function() {
    createBubbles();
    loadSavedTheme();
    
    // Réinitialiser l'ID du lobby pour éviter les conflits
    currentLobbyId = null;
    
    // Afficher un message de chargement
    updateCurrentLobbyDisplay();
    
    // Créer automatiquement une session à l'ouverture
    createSessionOnOpen();
    
    // Initialiser la connexion MQTT pour écouter les changements de statut
    initMqttForLobby();
    
    // Ajouter un event listener pour détecter la fermeture de la fenêtre
    window.addEventListener('beforeunload', function() {
        // Supprimer tous les lobbies quand la fenêtre se ferme
        deleteLobbyOnClose();
    });
});

// Create animated bubbles
function createBubbles() {
    const container = document.querySelector('.bubble-container');
    for (let i = 0; i < 18; i++) {
        const bubble = document.createElement('span');
        bubble.className = 'bubble';
        bubble.style.left = Math.random() * 100 + '%';
        bubble.style.width = bubble.style.height = (80 + Math.random() * 160) + 'px';
        bubble.style.animationDuration = (18 + Math.random() * 18) + 's';
        bubble.style.animationDelay = (-Math.random() * 20) + 's';
        container.appendChild(bubble);
    }
}

let players = [];
let gameState = "waiting";
let currentLobbyId = null; // Sera défini lors de la création de la session

function addPlayer() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const role = document.getElementById('role').value;
    const controllerId = document.getElementById('controller').value;
    
    // Validation des champs
    if (!username || !password) {
        showToast('Veuillez saisir un nom d\'utilisateur et un mot de passe.', 'warning');
        return;
    }

    if (!controllerId) {
        showToast('Veuillez sélectionner un contrôleur.', 'warning');
        return;
    }

    if (!role) {
        showToast('Veuillez sélectionner un rôle.', 'warning');
        return;
    }

    // Vérifier si le joueur n'est pas déjà dans la liste
    const existingPlayer = players.find(p => p.name === username);
    if (existingPlayer) {
        showToast(`${username} est déjà dans le lobby.`, 'warning');
        return;
    }

    // Vérifier si le lobby existe
    if (!currentLobbyId) {
        showToast('Lobby en cours de création. Veuillez patienter...', 'warning');
        return;
    }
    
    const lobbyId = currentLobbyId;

    // Afficher un message de chargement
    showToast('Vérification en cours...', 'info');

    // Appel API pour rejoindre le lobby avec vérifications
            fetch(`http://localhost:80/api/lobby/${lobbyId}/join`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            username: username,
            password: password,
            controllerId: parseInt(controllerId),
            role: role
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Utiliser les données retournées par l'API
            const userData = data.data;
            
            players.push({
                id: userData.id, // Ajouter l'ID du joueur
                name: userData.username,
                role: userData.role,
                controller: getControllerDisplayName(userData.controllerId),
                status: "waiting",
                score: 0,
                currentRound: 1,
                profilePicture: '/images/profiles/profile1.svg' // Photo de profil par défaut
            });

            updatePlayersTable();
            
            // Réinitialiser l'état du jeu si un nouveau joueur rejoint
            if (gameState === 'playing') {
                gameState = 'waiting';
                const gameButton = document.getElementById('gameButton');
                gameButton.textContent = 'Start Game';
                gameButton.disabled = false;
                gameButton.className = 'btn btn-game w-100 py-3';
                gameButton.style.background = '';
                gameButton.style.borderColor = '';
                gameButton.onclick = startGame;
                updateGameStatus();
            }
            
            // Vider les champs du formulaire
            document.getElementById('username').value = '';
            document.getElementById('password').value = '';
            document.getElementById('controller').value = '';
            
            showToast(`${userData.username} a été ajouté au lobby en tant que ${userData.role}.`, 'success');
            
            // Rafraîchir la liste des contrôleurs pour refléter le changement de statut
            loadAvailableControllers();
        } else {
            // Gestion des erreurs spécifiques
            let errorMessage = data.error;
            
            if (data.error.includes('Invalid username or password')) {
                errorMessage = 'Nom d\'utilisateur ou mot de passe incorrect.';
            } else if (data.error.includes('Controller is not available')) {
                errorMessage = 'Ce contrôleur n\'est pas disponible (hors ligne ou occupé).';
            } else if (data.error.includes('already in lobby')) {
                errorMessage = 'Ce joueur est déjà dans le lobby.';
            } else if (data.error.includes('Database error')) {
                errorMessage = 'Erreur de base de données. Veuillez réessayer.';
            }
            
            showToast(errorMessage, 'danger');
        }
    })
    .catch(error => {
        showToast('Erreur de connexion. Veuillez vérifier votre connexion et réessayer.', 'danger');
    });
}

function addBot() {
    const botName = `Bot_${Math.random().toString(36).substring(7)}`;
    players.push({
        name: botName,
        role: "player",
        controller: "Bot",
        status: "ready", 
        score: Math.floor(Math.random() * 300),
        currentRound: 1,
        profilePicture: '/images/profiles/profile2.svg' // Photo de profil pour les bots
    });
    updatePlayersTable();
    showToast(`${botName} has joined the game.`, 'success');
}



function startGame() {
    // Vérifier si un lobby existe
    if (!currentLobbyId) {
        showToast('❌ Aucun lobby actif. Veuillez créer un lobby d\'abord.', 'warning');
        return;
    }
    
    // Vérifier s'il y a des joueurs dans le lobby
    if (players.length === 0) {
        showToast('❌ Aucun joueur dans le lobby. Ajoutez des joueurs avant de démarrer le jeu.', 'warning');
        return;
    }
    
    // Vérifier si au moins un joueur est prêt
    const readyPlayers = players.filter(p => p.status === 'ready');
    if (readyPlayers.length === 0) {
        showToast('❌ Aucun joueur n\'est prêt. Les joueurs doivent être en statut "Ready" pour démarrer.', 'warning');
        return;
    }
    
    console.log('🎮 Starting game for lobby:', currentLobbyId);
    
    // Désactiver le bouton pendant le démarrage
    const gameButton = document.getElementById('gameButton');
    if (gameButton) {
        gameButton.disabled = true;
        gameButton.textContent = 'Starting Game...';
    }
    
    // Appel API pour démarrer le jeu
    fetch(`http://localhost:80/api/lobby/${currentLobbyId}/game/start`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Mettre à jour l'état du jeu
            gameState = 'playing';
            updateGameStatus();
            showToast('🎮 Game started successfully!', 'success');
            
            // Mettre à jour le bouton
            if (gameButton) {
                gameButton.textContent = 'Game in Progress';
                gameButton.className = 'btn btn-danger w-100 py-3';
                gameButton.disabled = true;
            }
            
            // Publier un message MQTT pour informer tous les clients
            if (window.mqttClient && window.mqttClient.connected) {
                const gameStartMessage = {
                    action: 'game_started',
                    lobbyId: currentLobbyId,
                    timestamp: Date.now()
                };
                window.mqttClient.publish('group-05/lobby/' + currentLobbyId + '/game', JSON.stringify(gameStartMessage));
            }
            
            console.log('✅ Game started successfully:', data);
        } else {
            showToast('❌ Failed to start game: ' + (data.error || 'Unknown error'), 'danger');
            console.error('❌ Failed to start game:', data);
            
            // Réactiver le bouton en cas d'échec
            if (gameButton) {
                gameButton.disabled = false;
                gameButton.textContent = 'Start Game';
                gameButton.className = 'btn btn-game w-100 py-3';
            }
        }
    })
    .catch(error => {
        showToast('❌ Error starting game: ' + error.message, 'danger');
        console.error('❌ Error starting game:', error);
        
        // Réactiver le bouton en cas d'erreur
        if (gameButton) {
            gameButton.disabled = false;
            gameButton.textContent = 'Start Game';
            gameButton.className = 'btn btn-game w-100 py-3';
        }
    });
}



function updateGameStatus() {
    const gameStatusElement = document.getElementById('gameStatus');
    const lobbyIdDisplay = document.getElementById('lobbyIdDisplay');
    
    // Mettre à jour le Lobby ID dans la barre de navigation (si l'élément existe)
    if (lobbyIdDisplay && currentLobbyId) {
        lobbyIdDisplay.textContent = `Lobby #${currentLobbyId}`;
        lobbyIdDisplay.style.display = 'inline-block'; // Afficher l'élément
    } else if (lobbyIdDisplay) {
        lobbyIdDisplay.style.display = 'none'; // Masquer l'élément
    }
    
    // Mettre à jour le Game Status (seulement l'état du jeu)
    if (gameStatusElement) {
        switch (gameState) {
            case 'waiting':
                gameStatusElement.textContent = 'Waiting';
                gameStatusElement.className = 'badge status-waiting';
                break;
            case 'playing':
                gameStatusElement.textContent = 'Playing';
                gameStatusElement.className = 'badge status-playing';
                break;
            case 'finished':
                gameStatusElement.textContent = 'Finished';
                gameStatusElement.className = 'badge status-finished';
                break;
            default:
                gameStatusElement.textContent = 'Finished';
                gameStatusElement.className = 'badge status-finished';
        }
    }
}

function loadAvailableControllers() {
    const controllerSelect = document.getElementById('controller');
    if (!controllerSelect) {
        return;
    }
    
    fetch('http://localhost:80/api/controller/list', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            // Clear existing options except the first one
            controllerSelect.innerHTML = '<option value="">Select a controller...</option>';
            
            // Add only available controllers (status = "online")
            data.data.forEach(controller => {
                if (controller.status === "online") {
                    const option = document.createElement('option');
                    option.value = controller.id;
                    option.textContent = `Controller ${controller.id}`;
                    controllerSelect.appendChild(option);
                }
            });
        } else {
            // Failed to load controllers
        }
    })
    .catch(error => {
        // Error loading controllers
    });
}

function getControllerDisplayName(controllerId) {
    return `Controller ${controllerId}`;
}

// Fonction pour extraire l'ID du contrôleur depuis le nom (ex: "Controller 1" -> "1")
function extractControllerIdFromName(controllerName) {
    const match = controllerName.match(/Controller (\d+)/);
    return match ? match[1] : null;
}

// Fonction pour mettre à jour le statut d'un contrôleur
function updateControllerStatus(controllerId, status, lobbyId) {
    fetch(`http://localhost:80/api/controller/${controllerId}/status`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            status: status,
            lobbyId: lobbyId
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            console.log(`✅ Controller ${controllerId} status updated to ${status}`);
        } else {
            console.error(`❌ Failed to update controller status:`, data.error);
        }
    })
    .catch(error => {
        console.error('❌ Error updating controller status:', error);
    });
}

function updateCurrentLobbyDisplay() {
    updateGameStatus();
    
    // Mettre à jour le badge dans la navbar avec l'ID du lobby
    const lobbyBadge = document.getElementById('lobbyBadge');
    if (lobbyBadge && currentLobbyId) {
        lobbyBadge.textContent = `Lobby #${currentLobbyId}`;
    } else if (lobbyBadge) {
        lobbyBadge.textContent = 'Creating Lobby...';
    }
}

function deleteLobbyOnClose() {
    // Supprimer le lobby actuel quand la fenêtre se ferme
    if (!currentLobbyId) {
        return; // Pas de lobby à supprimer
    }
    
    const data = JSON.stringify({});
            navigator.sendBeacon(`http://localhost:80/api/lobby/${currentLobbyId}/delete`, data);
    
    // Alternative avec fetch synchrone si sendBeacon n'est pas supporté
    if (!navigator.sendBeacon) {
        const xhr = new XMLHttpRequest();
        xhr.open('DELETE', `http://localhost:80/api/lobby/${currentLobbyId}`, false);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send(data);
    }
}

function createSessionOnOpen() {
    console.log('🔄 Creating new lobby session...');
    
    // Créer une nouvelle session/lobby automatiquement
    fetch('http://localhost:80/api/lobby/create', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({})
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            const roomId = data.data.id;
            currentLobbyId = roomId; // Utiliser l'ID réel du lobby créé
            console.log('✅ Lobby created with ID:', roomId);
            
            // Mettre à jour l'affichage avec le vrai ID
            updateCurrentLobbyDisplay();
            
            // Charger les contrôleurs disponibles maintenant que le lobby existe
            loadAvailableControllers();
            
            // Initialiser le tableau des joueurs
            updatePlayersTable();
            
            // S'abonner au topic de jeu du nouveau lobby
            if (window.mqttClient && window.mqttClient.connected) {
                window.mqttClient.subscribe(`group-05/lobby/${roomId}/game`, (err) => {
                    if (err) {
                        console.error('Error subscribing to lobby game topic:', err);
                    } else {
                        console.log(`Subscribed to lobby game topic: group-05/lobby/${roomId}/game`);
                    }
                });
            }
            
            showToast(`Session créée avec succès ! ID: ${roomId}`, 'success');
        } else {
            console.error('❌ Failed to create session:', data);
            showToast('Failed to create session: ' + (data.error || 'Unknown error'), 'danger');
        }
    })
    .catch(error => {
        console.error('❌ Error creating session:', error);
        showToast('Failed to create session. Please refresh the page.', 'danger');
    });
}

function updatePlayersTable() {
    const tbody = document.getElementById('playersTable');
    const playerCount = document.getElementById('playerCount');
    
    // Récupérer les données du backend pour chaque joueur
    if (currentLobbyId && players.length > 0) {
        players.forEach(player => {
            // Récupérer les données du joueur depuis le backend
            fetch(`http://localhost:80/api/lobby/${currentLobbyId}/players/${player.id}/status`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                }
            })
            .then(response => response.json())
            .then(data => {
                if (data.success && data.data) {
                    // Mettre à jour les données du joueur avec celles du backend
                    player.status = data.data.status;
                    player.score = data.data.score || 0;
                    player.currentRound = data.data.currentRound || 1;
                }
            })
            .catch(error => {
                // Error fetching player data
            });
        });
    }
    
    tbody.innerHTML = players.map(player => `
        <tr class="${player.eliminated ? 'table-secondary' : ''}">
            <td>
                <div class="profile-picture-container" onclick="openProfilePictureSelection(${player.id})">
                    <img src="${player.profilePicture || '/images/profiles/profile1.svg'}" 
                         alt="${player.name}" 
                         class="profile-picture-small ${player.eliminated ? 'eliminated' : ''}"
                         title="Click to change profile picture">
                </div>
            </td>
            <td class="fw-semibold">${player.name}</td>
            <td><span class="badge bg-primary">${player.role}</span></td>
            <td>${player.controller}</td>
            <td>
                ${player.eliminated ? 
                    '<span class="badge bg-danger">Eliminated</span>' : 
                    `<span class="badge status-${player.status}">${player.status}</span>`
                }
            </td>
            <td class="${player.eliminated ? 'text-muted' : ''}">${player.score || 0}</td>
            <td class="${player.eliminated ? 'text-muted' : ''}">${player.currentRound || 1}</td>
            <td>
                ${player.eliminated ? 
                    '<span class="text-muted small">Final Score</span>' :
                    `<button class="btn btn-sm btn-outline-primary me-1" onclick="editPlayer('${player.name}')">Edit</button>
                     <button class="btn btn-sm btn-outline-danger" onclick="removePlayer('${player.name}')">Remove</button>`
                }
            </td>
        </tr>
    `).join('');
    
    playerCount.textContent = players.length;
}

// Fonction pour rafraîchir les données des joueurs depuis la base de données
function refreshPlayersData() {
    if (!currentLobbyId) return;
    
    fetch(`http://localhost:80/api/lobby/${currentLobbyId}/players`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.data) {
            // Mettre à jour les données des joueurs avec celles de la base de données
            data.data.forEach(dbPlayer => {
                const localPlayer = players.find(p => p.id === dbPlayer.id);
                if (localPlayer) {
                    localPlayer.status = dbPlayer.status;
                    localPlayer.score = dbPlayer.score || 0;
                    localPlayer.currentRound = dbPlayer.round || 1;
                }
            });
            
            // Mettre à jour l'affichage
            updatePlayersTable();
        }
    })
    .catch(error => {
        console.error('Error refreshing players data:', error);
    });
}

function editPlayer(playerName) {
    const player = players.find(p => p.name === playerName);
    if (player) {
        // Ouvrir la modal de profil avec les données du joueur
        openEditProfileModal(player.id);
    }
}

function updatePlayer(playerName) {
    const role = document.getElementById('role').value;
    const controllerId = document.getElementById('controller').value;
    
    const playerIndex = players.findIndex(p => p.name === playerName);
    if (playerIndex !== -1) {
        players[playerIndex].role = role;
        players[playerIndex].controller = getControllerDisplayName(controllerId);
        
        updatePlayersTable();
        
        // Réinitialiser le formulaire
        document.getElementById('username').value = '';
        document.getElementById('password').value = '';
        document.getElementById('role').value = 'player';
        document.getElementById('controller').value = '';
        
        // Remettre le bouton original
        const addButton = document.querySelector('button[onclick="updatePlayer(\'' + playerName + '\')"]');
        addButton.textContent = 'Add Player to Lobby';
        addButton.onclick = addPlayer;
        
        showToast(`${playerName} updated successfully`, 'success');
    }
}

function removePlayer(playerName) {
    if (confirm(`Are you sure you want to remove ${playerName} from the lobby? This will free up their controller.`)) {
        const player = players.find(p => p.name === playerName);
        if (player && currentLobbyId) {
            console.log(`🗑️ Removing player ${playerName} (ID: ${player.id}) from lobby ${currentLobbyId}`);
            
            // Extraire l'ID du contrôleur depuis le nom du contrôleur
            const controllerId = extractControllerIdFromName(player.controller);
            
            // Appel API pour supprimer le joueur du lobby (lobbies_players)
            fetch(`http://localhost:80/api/lobby/${currentLobbyId}/players/${player.id}`, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json',
                }
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    console.log(`✅ Player ${playerName} removed successfully`);
                    
                    // Mettre à jour le statut du contrôleur à "online" et retirer l'ID du lobby
                    if (controllerId) {
                        updateControllerStatus(controllerId, 'online', null);
                    }
                    
                    // Supprimer du tableau local
                    players = players.filter(p => p.name !== playerName);
                    updatePlayersTable();
                    showToast(`${playerName} has been removed from the lobby and their controller has been freed.`, 'success');
                    
                    // Rafraîchir la liste des contrôleurs disponibles
                    loadAvailableControllers();
                } else {
                    console.error(`❌ Failed to remove player:`, data.error);
                    showToast(`Failed to remove player from lobby: ${data.error}`, 'danger');
                }
            })
            .catch(error => {
                console.error('❌ Error removing player from lobby:', error);
                showToast('Error removing player from lobby. Please try again.', 'danger');
            });
        } else {
            console.log(`⚠️ No current lobby or player not found, removing from local array only`);
            // Si pas de lobby actuel, supprimer seulement du tableau local
            players = players.filter(p => p.name !== playerName);
            updatePlayersTable();
            showToast(`${playerName} has been removed from the lobby.`, 'success');
        }
    }
}

// Game settings variables
let gameSettings = {
    lobbyName: "Simon Game Lobby",
    roundDuration: 30,
    maxPlayers: 4,
    speedMultiplier: 1.0,
    sequenceLength: 5
};

function openGameSettings() {
    // Remplir la modal avec les paramètres actuels
    document.getElementById('roundDuration').value = gameSettings.roundDuration;
    document.getElementById('maxPlayers').value = gameSettings.maxPlayers;
    document.getElementById('speedMultiplier').value = gameSettings.speedMultiplier;
    document.getElementById('numSequences').value = gameSettings.sequenceLength;
    
    // Ouvrir la modal
    const modal = new bootstrap.Modal(document.getElementById('gameSettingsModal'));
    modal.show();
}

function saveGameSettings() {
    // Récupérer les nouvelles valeurs
    gameSettings.roundDuration = parseInt(document.getElementById('roundDuration').value);
    gameSettings.maxPlayers = parseInt(document.getElementById('maxPlayers').value);
    gameSettings.speedMultiplier = parseFloat(document.getElementById('speedMultiplier').value);
    gameSettings.sequenceLength = parseInt(document.getElementById('numSequences').value);
    
    // Fermer la modal
    const modal = bootstrap.Modal.getInstance(document.getElementById('gameSettingsModal'));
    modal.hide();
    
    showToast('Game settings saved successfully!', 'success');
}

// Theme toggle functionality
function toggleTheme() {
    const body = document.body;
    const themeToggle = document.getElementById('themeToggle');
    const icon = themeToggle.querySelector('i');
    
    if (body.classList.contains('dark-theme')) {
        // Switch to light theme
        body.classList.remove('dark-theme');
        icon.className = 'fas fa-moon';
        localStorage.setItem('theme', 'light');
        showToast('Mode clair activé', 'success');
        
        // Update button appearance for light theme - dark icon
        themeToggle.className = 'btn btn-outline-dark btn-sm';
        icon.style.color = '#000000';
    } else {
        // Switch to dark theme
        body.classList.add('dark-theme');
        icon.className = 'fas fa-sun';
        localStorage.setItem('theme', 'dark');
        showToast('Mode sombre activé', 'success');
        
        // Update button appearance for dark theme - light icon
        themeToggle.className = 'btn btn-outline-light btn-sm';
        icon.style.color = '#ffffff';
    }
}

// Load saved theme on page load
function loadSavedTheme() {
    const savedTheme = localStorage.getItem('theme');
    const themeToggle = document.getElementById('themeToggle');
    const icon = themeToggle.querySelector('i');
    
    if (savedTheme === 'dark') {
        document.body.classList.add('dark-theme');
        icon.className = 'fas fa-sun';
        themeToggle.className = 'btn btn-outline-light btn-sm';
        icon.style.color = '#ffffff';
    } else {
        document.body.classList.remove('dark-theme');
        icon.className = 'fas fa-moon';
        themeToggle.className = 'btn btn-outline-dark btn-sm';
        icon.style.color = '#000000';
    }
}

function showToast(message, type) {
    // Simple toast notification
    const toast = document.createElement('div');
    toast.className = `alert alert-${type} position-fixed top-0 end-0 m-3`;
    toast.style.zIndex = '9999';
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

function showRegisterModal() {
    const modal = new bootstrap.Modal(document.getElementById('registerModal'));
    modal.show();
}

function registerUser() {
    const username = document.getElementById('registerUsername').value;
    const password = document.getElementById('registerPassword').value;
    const confirmPassword = document.getElementById('registerConfirmPassword').value;
    
    // Validation
    if (!username || !password || !confirmPassword) {
        showToast('Please fill in all fields.', 'danger');
        return;
    }
    
    if (password !== confirmPassword) {
        showToast('Passwords do not match.', 'danger');
        return;
    }
    
    if (password.length < 6) {
        showToast('Password must be at least 6 characters long.', 'danger');
        return;
    }
    
    // Appel API pour l'enregistrement
            fetch('http://localhost:80/api/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            username: username,
            password: password
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.error) {
            showToast(data.error, 'danger');
        } else {
            showToast('Account created successfully!', 'success');
            
            // Ajouter le joueur à la liste
            players.push({
                name: username,
                role: "player",
                controller: "Web Controller",
                status: "ready",
                score: 0,
                currentRound: 1,
                profilePicture: '/images/profiles/profile1.svg' // Photo de profil par défaut
            });
            
            updatePlayersTable();
            
            // Fermer la modal et réinitialiser le formulaire
            const modal = bootstrap.Modal.getInstance(document.getElementById('registerModal'));
            modal.hide();
            
            // Réinitialiser le formulaire
            document.getElementById('registerForm').reset();
        }
    })
    .catch(error => {
        showToast('Failed to create account. Please try again.', 'danger');
    });
}

// Initialize
createBubbles();
updatePlayersTable();
updateCurrentLobbyDisplay(); // Initialiser l'affichage du lobby

// Refresh controllers list every 5 seconds
setInterval(loadAvailableControllers, 5000);

// Refresh players data every 3 seconds during game
setInterval(() => {
    if (gameState === 'playing') {
        refreshPlayersData();
    }
}, 3000);

// Fonction pour initialiser la connexion MQTT dans le lobby
function initMqttForLobby() {
    
    
    // Vérifier si mqtt est disponible
    if (typeof mqtt === 'undefined') {
        
        
        return;
    }
    
    const options = {
        username: "group-05",
        password: "X8y!dF5zLp2A",
        reconnectPeriod: 3000,
        connectTimeout: 4000,
        clean: true,
        clientId: "lobby-" + Math.random().toString(16).substr(2, 8)
    };

    
    
    
    window.mqttClient = mqtt.connect("ws://iti-mqtt.mni.thm.de:9001", options);

    window.mqttClient.on("connect", () => {
        
        
        
        
        // S'abonner au topic des statuts de joueur
        window.mqttClient.subscribe('group-05/players/status', (err) => {
            if (err) {
                // Error subscribing to player status topic
            } else {
                
            }
        });
        
        // S'abonner au topic des timeouts de joueur
                    window.mqttClient.subscribe('group-05/players/timeout', (err) => {
                if (err) {
                    // Error subscribing to player timeout topic
                } else {
                    
                }
            });
            
            window.mqttClient.subscribe('group-05/players/score', (err) => {
                if (err) {
                    // Error subscribing to player score topic
                } else {
                    
                }
            });
            
            // S'abonner au topic de jeu du lobby
            if (currentLobbyId) {
                window.mqttClient.subscribe(`group-05/lobby/${currentLobbyId}/game`, (err) => {
                    if (err) {
                        console.error('Error subscribing to lobby game topic:', err);
                    } else {
                        console.log(`Subscribed to lobby game topic: group-05/lobby/${currentLobbyId}/game`);
                    }
                });
            }
    });

    window.mqttClient.on("error", err => {
        // MQTT lobby error
    });

    window.mqttClient.on('message', (topic, message) => {
        
        
        if (topic === 'group-05/players/status') {
            try {
                const data = JSON.parse(message.toString());
                
                
                
                // Message details logged
                
                // Mettre à jour le statut du joueur dans la liste
                if (data.action === 'player_status_updated') {
                    
                    
                    
                    
                    
                    // Vérifier si le message concerne notre lobby
                    if (data.lobbyId == currentLobbyId) {
                        
                        
                        const player = players.find(p => p.id == data.playerId);
                        
                        
                        if (player) {
                            
                            player.status = data.status;
                            updatePlayersTable();
                            showToast(`Player ${player.name} status updated to: ${data.status}`, 'info');
                        } else {
                            
                            
                            // Players IDs logged
                        }
                    } else {
                        
                    }
                }
            } catch (error) {
                // Error parsing MQTT message
            }
        } else if (topic === `group-05/lobby/${currentLobbyId}/game`) {
            try {
                const data = JSON.parse(message.toString());
                
                if (data.action === 'game_end') {
                    console.log('🏁 Game ended for lobby:', currentLobbyId);
                    
                    // Mettre à jour l'état du jeu
                    gameState = 'finished';
                    updateGameStatus();
                    
                    // Réinitialiser le bouton de jeu
                    const gameButton = document.getElementById('gameButton');
                    if (gameButton) {
                        gameButton.innerHTML = '<i class="fas fa-play me-2"></i>Start Game';
                        gameButton.disabled = false;
                        gameButton.className = 'btn btn-game w-100 py-3';
                        gameButton.onclick = startGame;
                    }
                    
                    showToast(`🏁 Game ended after ${data.finalRound} rounds!`, 'info');
                } else if (data.action === 'round_end') {
                    console.log('🏁 Round ended:', data.round);
                    showToast(`Round ${data.round} ended!`, 'info');
                }
            } catch (error) {
                console.error('Error parsing game MQTT message:', error);
            }
        } else if (topic === 'group-05/players/timeout') {
            try {
                const data = JSON.parse(message.toString());
                
                
                if (data.action === 'player_timeout') {
                    
                    
                    // Supprimer le joueur de la liste
                    const playerIndex = players.findIndex(p => p.id === data.playerId);
                    if (playerIndex !== -1) {
                        const player = players[playerIndex];
                        players.splice(playerIndex, 1);
                        updatePlayersTable();
                        showToast(`Player ${player.name} was removed due to timeout (not ready within 30 seconds)`, 'warning');
                        
                        // Rafraîchir la liste des contrôleurs
                        loadAvailableControllers();
                    }
                }
            } catch (error) {
                // Error parsing timeout MQTT message
            }
        } else if (topic === 'group-05/players/score') {
            try {
                const data = JSON.parse(message.toString());
                
                
                if (data.action === 'player_score_updated' && data.lobbyId == currentLobbyId) {
                    
                    
                    const player = players.find(p => p.id == data.playerId);
                    if (player) {
                        
                        
                        
                        
                        player.score = data.score;
                        player.currentRound = data.currentRound;
                        
                        // Mettre à jour l'affichage en temps réel
                        updatePlayersTable();
                        
                        
                    } else {
                        
                    }
                }
            } catch (error) {
                // Error parsing score MQTT message
            }
        }
    });
}



// Fonction pour sauvegarder les changements de profil
function savePlayerProfile() {
    const currentPassword = document.getElementById('editCurrentPassword').value;
    const newPassword = document.getElementById('editNewPassword').value;
    const confirmPassword = document.getElementById('editConfirmPassword').value;
    const selectedProfilePicture = document.querySelector('.profile-picture.selected');
    const playerId = document.getElementById('editProfileModal').getAttribute('data-player-id');
    
    // Récupérer le username original (non modifiable)
    const player = players.find(p => p.id == playerId);
    if (!player) {
        showToast('Player not found', 'danger');
        return;
    }
    const username = player.name; // Username non modifiable
    
    // Validation
    if (!currentPassword.trim()) {
        showToast('Current password is required', 'warning');
        return;
    }
    
    if (newPassword && newPassword !== confirmPassword) {
        showToast('New passwords do not match', 'warning');
        return;
    }
    
    if (newPassword && newPassword.length < 6) {
        showToast('New password must be at least 6 characters', 'warning');
        return;
    }
    
    const profilePictureUrl = selectedProfilePicture ? selectedProfilePicture.getAttribute('data-url') : null;
    
    // Appel API pour sauvegarder les changements
    fetch(`http://localhost:81/api/profile`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            currentUsername: username,
            currentPassword: currentPassword,
            newUsername: username, // Username inchangé
            newPassword: newPassword || null,
            newProfileUrl: profilePictureUrl
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('Profile updated successfully!', 'success');
            
            // Mettre à jour le tableau local (seulement la photo de profil)
            if (player) {
                if (profilePictureUrl) {
                    player.profilePicture = profilePictureUrl;
                }
                updatePlayersTable();
            }
            
            // Fermer la modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('editProfileModal'));
            modal.hide();
        } else {
            showToast(`Failed to update profile: ${data.error}`, 'danger');
        }
    })
    .catch(error => {
        showToast('Error updating profile. Please try again.', 'danger');
    });
}





// Fonction pour ouvrir la modal de profil avec les données du joueur
function openEditProfileModal(playerId) {
    const player = players.find(p => p.id === playerId);
    if (player) {
        // Afficher le username en lecture seule
        const usernameField = document.getElementById('editUsername');
        usernameField.value = player.name;
        
        document.getElementById('editCurrentPassword').value = '';
        document.getElementById('editNewPassword').value = '';
        document.getElementById('editConfirmPassword').value = '';
        
        // Charger les photos de profil prédéfinies
        loadProfilePictures(player.profilePicture);
        
        // Stocker l'ID du joueur pour les opérations de sauvegarde/suppression
        document.getElementById('editProfileModal').setAttribute('data-player-id', playerId);
        
        const modal = new bootstrap.Modal(document.getElementById('editProfileModal'));
        modal.show();
    }
}

// Fonction pour charger les photos de profil prédéfinies
function loadProfilePictures(currentProfilePicture) {
    const grid = document.getElementById('profilePictureGrid');
    grid.innerHTML = '';
    
    // Charger directement les images locales au lieu d'utiliser l'API
    const profilePictures = {
        1: '/images/profiles/profile1.svg',
        2: '/images/profiles/profile2.svg',
        3: '/images/profiles/profile3.svg',
        4: '/images/profiles/profile4.svg',
        5: '/images/profiles/profile5.svg',
        6: '/images/profiles/profile6.svg',
        7: '/images/profiles/profile7.svg',
        8: '/images/profiles/profile8.svg',
        9: '/images/profiles/profile9.svg',
        10: '/images/profiles/profile10.svg'
    };
    
    Object.entries(profilePictures).forEach(([id, url]) => {
        const col = document.createElement('div');
        col.className = 'col-3';
        
        const img = document.createElement('img');
        img.src = url;
        img.className = 'profile-picture img-fluid rounded border';
        img.style.cursor = 'pointer';
        img.style.width = '60px';
        img.style.height = '60px';
        img.setAttribute('data-url', url);
        
        // Marquer comme sélectionné si c'est la photo actuelle
        if (currentProfilePicture && currentProfilePicture.includes(`profile${id}.svg`)) {
            img.classList.add('selected');
            img.style.border = '3px solid #007bff';
        } else if (id === '1' && !currentProfilePicture) {
            // Sélectionner la première image par défaut si aucune n'est sélectionnée
            img.classList.add('selected');
            img.style.border = '3px solid #007bff';
        }
        
        img.addEventListener('click', function() {
            // Retirer la sélection de toutes les images
            document.querySelectorAll('.profile-picture').forEach(pic => {
                pic.classList.remove('selected');
                pic.style.border = '1px solid #dee2e6';
            });
            
            // Sélectionner cette image
            this.classList.add('selected');
            this.style.border = '3px solid #007bff';
        });
        
        col.appendChild(img);
        grid.appendChild(col);
    });
}

// Fonction pour éliminer un joueur (pause le jeu pour ce joueur)
function eliminatePlayer(playerId) {
    const player = players.find(p => p.id === playerId);
    if (player && !player.eliminated) {
        
        // Marquer le joueur comme éliminé
        player.eliminated = true;
        player.status = 'eliminated';
        
        // Envoyer un message MQTT pour informer le contrôleur
        if (typeof mqtt !== 'undefined' && mqttClient) {
            const eliminationMessage = {
                action: 'player_eliminated',
                playerId: playerId,
                lobbyId: currentLobbyId,
                finalScore: player.score,
                finalRound: player.currentRound,
                timestamp: Date.now()
            };
            
            mqttClient.publish('group-05/game/elimination', JSON.stringify(eliminationMessage), (err) => {
                if (err) {
                    // Error sending elimination message
                } else {
                    
                }
            });
        }
        
        // Mettre à jour l'affichage
        updatePlayersTable();
        
        // Vérifier si tous les joueurs sont éliminés
        checkGameEndCondition();
        
        showToast(`🏁 ${player.name} has been eliminated! Final score: ${player.score}`, 'warning');
    }
}

// Fonction pour vérifier si tous les joueurs sont éliminés et transformer le bouton
function checkGameEndCondition() {
    if (gameState !== 'playing') return;
    
    // Vérifier si tous les joueurs sont éliminés (gardent leur score et round)
    const activePlayers = players.filter(player => !player.eliminated);
    
    
    
    // Eliminated players count logged
    
    
    if (activePlayers.length === 0 && players.length > 0) {
        
        
        // Transformer automatiquement le bouton en Start Game
        const gameButton = document.getElementById('gameButton');
        gameButton.innerHTML = '<i class="fas fa-play me-2"></i>Start Game';
        gameButton.disabled = false;
        gameButton.className = 'btn btn-game w-100 py-3';
        gameButton.style.background = '';
        gameButton.style.border = '';
        gameButton.style.color = '';
        gameButton.onclick = startGame;
        
        // Mettre à jour l'état du jeu
        gameState = 'finished';
        updateGameStatus();
        
        // Afficher les résultats finaux
        showFinalResults();
        
        showToast('🏁 All players eliminated! Game ended automatically.', 'info');
    } else {
        // Continuer à vérifier toutes les 2 secondes
        setTimeout(checkGameEndCondition, 2000);
    }
}

// Fonction pour réinitialiser le jeu après la fin
function resetGameAfterEnd() {
    // Réinitialiser l'état du jeu
    gameState = 'waiting';
    
    // Réinitialiser tous les joueurs
    players.forEach(player => {
        player.eliminated = false;
        player.status = 'waiting';
        player.score = 0;
        player.currentRound = 1;
    });
    
    // Mettre à jour l'affichage
    updatePlayersTable();
    updateGameStatus();
    
    // Réinitialiser le bouton de jeu
    const gameButton = document.getElementById('gameButton');
    if (gameButton) {
        gameButton.innerHTML = '<i class="fas fa-play me-2"></i>Start Game';
        gameButton.disabled = false;
        gameButton.className = 'btn btn-game w-100 py-3';
        gameButton.onclick = startGame;
    }
    
    showToast('🔄 Game reset - ready for a new game!', 'success');
}

// Fonction pour afficher les résultats finaux
function showFinalResults() {
    
    
    // Trier les joueurs par score (décroissant)
    const sortedPlayers = [...players].sort((a, b) => b.score - a.score);
    
    sortedPlayers.forEach((player, index) => {
        
    });
    
    // Afficher le gagnant
    if (sortedPlayers.length > 0) {
        const winner = sortedPlayers[0];
        showToast(`🏆 Winner: ${winner.name} with ${winner.score} points!`, 'success');
    }
}

// Fonction pour envoyer les séquences à tous les joueurs prêts
function sendSequencesToPlayers() {
    
    
    // Récupérer les paramètres de jeu
    const sequenceLength = gameSettings.sequenceLength || 5;
    const roundDuration = gameSettings.roundDuration || 30;
    
    // Game settings logged
    
    // Envoyer les séquences à chaque joueur actif (non éliminé)
    players.forEach(player => {
        if (player.status === 'ready' && !player.eliminated) {
            // Sending sequence to player logged
            
            // Générer une séquence aléatoire pour ce joueur
            const sequence = generateRandomSequence(sequenceLength);
            
            // Envoyer la séquence via MQTT au contrôleur du joueur
            if (typeof mqtt !== 'undefined' && mqttClient) {
                const sequenceMessage = {
                    action: 'game_sequence',
                    playerId: player.id,
                    lobbyId: currentLobbyId,
                    sequence: sequence,
                    roundDuration: roundDuration,
                    timestamp: Date.now()
                };
                
                mqttClient.publish('group-05/game/sequence', JSON.stringify(sequenceMessage), (err) => {
                    if (err) {
                        // Error sending sequence to player
                    } else {
                        
                    }
                });
            }
            
            // Mettre à jour le statut du joueur à "playing"
            updatePlayerStatus(player.id, 'playing');
        } else if (player.eliminated) {
            // Skipping eliminated player - NO SEQUENCE SENT
        } else {
            // Skipping non-ready player
        }
    });
    
    showToast(`📤 Sequences sent to ${players.filter(p => p.status === 'ready').length} players!`, 'success');
}

// Fonction pour générer une séquence aléatoire
function generateRandomSequence(length) {
    const colors = ['red', 'blue', 'green', 'yellow'];
    const sequence = [];
    
    for (let i = 0; i < length; i++) {
        const randomColor = colors[Math.floor(Math.random() * colors.length)];
        sequence.push(randomColor);
    }
    
    return sequence;
}

// Fonction pour mettre à jour le statut d'un joueur
function updatePlayerStatus(playerId, newStatus) {
    if (!currentLobbyId) return;
    
    fetch(`http://localhost:80/api/lobby/${currentLobbyId}/players/${playerId}/status`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            status: newStatus
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            
            
            // Mettre à jour le statut local
            const player = players.find(p => p.id === playerId);
            if (player) {
                player.status = newStatus;
                updatePlayersTable();
            }
        } else {
            // Failed to update player status
        }
    })
    .catch(error => {
        // Error updating player status
    });
}

// Fonction pour ouvrir la sélection de photo de profil
function openProfilePictureSelection(playerId) {
    const player = players.find(p => p.id === playerId);
    if (!player) return;
    
    // Stocker l'ID du joueur pour la sélection
    document.getElementById('profilePictureModal').setAttribute('data-player-id', playerId);
    
    // Charger les photos de profil disponibles
    loadProfilePictureSelection(player.profilePicture);
    
    // Afficher la modal
    const modal = document.getElementById('profilePictureModal');
    modal.classList.add('show');
}

// Fonction pour fermer la modal de sélection de photo de profil
function closeProfilePictureModal() {
    const modal = document.getElementById('profilePictureModal');
    modal.classList.remove('show');
}

// Fonction pour charger les photos de profil dans la modal de sélection
function loadProfilePictureSelection(currentProfilePicture) {
    const grid = document.getElementById('profilePictureSelectionGrid');
    grid.innerHTML = '';
    
    // Charger directement les images locales au lieu d'utiliser l'API
    const profilePictures = {
        1: '/images/profiles/profile1.svg',
        2: '/images/profiles/profile2.svg',
        3: '/images/profiles/profile3.svg',
        4: '/images/profiles/profile4.svg',
        5: '/images/profiles/profile5.svg',
        6: '/images/profiles/profile6.svg',
        7: '/images/profiles/profile7.svg',
        8: '/images/profiles/profile8.svg',
        9: '/images/profiles/profile9.svg',
        10: '/images/profiles/profile10.svg'
    };
    
    Object.entries(profilePictures).forEach(([id, url]) => {
        const img = document.createElement('img');
        img.src = url;
        img.className = 'profile-picture-option';
        img.setAttribute('data-url', url);
        img.setAttribute('data-id', id);
        
        // Marquer comme sélectionné si c'est la photo actuelle
        if (currentProfilePicture && currentProfilePicture.includes(`profile${id}.svg`)) {
            img.classList.add('selected');
        } else if (id === '1' && !currentProfilePicture) {
            // Sélectionner la première image par défaut si aucune n'est sélectionnée
            img.classList.add('selected');
        }
        
        img.addEventListener('click', function() {
            // Retirer la sélection de toutes les images
            document.querySelectorAll('.profile-picture-option').forEach(pic => {
                pic.classList.remove('selected');
            });
            
            // Sélectionner cette image
            this.classList.add('selected');
        });
        
        grid.appendChild(img);
    });
}

// Fonction pour sauvegarder la nouvelle photo de profil
function saveProfilePicture() {
    const playerId = document.getElementById('profilePictureModal').getAttribute('data-player-id');
    const selectedPicture = document.querySelector('.profile-picture-option.selected');
    
    if (!selectedPicture) {
        showToast('Please select a profile picture', 'warning');
        return;
    }
    
    const profilePictureUrl = selectedPicture.getAttribute('data-url');
    const player = players.find(p => p.id == playerId);
    
    if (!player) {
        showToast('Player not found', 'danger');
        return;
    }
    
    // Mettre à jour la photo de profil du joueur
    player.profilePicture = profilePictureUrl;
    
    // Ajouter l'animation de changement
    const profileImg = document.querySelector(`[onclick="openProfilePictureSelection(${playerId})"] img`);
    if (profileImg) {
        profileImg.classList.add('profile-picture-changing');
        setTimeout(() => {
            profileImg.classList.remove('profile-picture-changing');
        }, 600);
    }
    
    // Mettre à jour l'affichage
    updatePlayersTable();
    
    // Fermer la modal
    closeProfilePictureModal();
    
    showToast('Profile picture updated successfully!', 'success');
}

// Fermer la modal en cliquant à l'extérieur
document.addEventListener('DOMContentLoaded', function() {
    const modal = document.getElementById('profilePictureModal');
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            closeProfilePictureModal();
        }
    });
});
