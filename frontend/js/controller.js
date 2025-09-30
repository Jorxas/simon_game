// Variables globales du jeu
let gameState = "waiting"; // waiting, playing, eliminated, finished
let currentRound = 1;
let globalScore = 0; // Score global qui s'accumule à travers les rounds
let playerScore = 0; // Score du round actuel seulement
let currentSequence = [];
let playerSequence = [];
let playerEliminated = false;
let isSequencePlaying = false;
let roundTimeout = null;
let roundStartTime = 0;
let currentGameId = null;
let playerLobbyId = null;
let playerId = null;
let finalScore = 0;
let finalRound = 1;
let roundDuration = 30000; // 30 secondes par défaut
let gameTimer = 0; // Timer global du jeu
let playerReady = false;
let mqttClient = null;

// Fonction pour démarrer le jeu
function startGame() {
    if (gameState !== "waiting") {
        console.log('❌ Game already in progress');
        return;
    }
    
    console.log('🎮 Starting game...');
    gameState = "playing";
    playerEliminated = false;
    globalScore = 0;
    currentRound = 1;
    playerScore = 0;
    
    // Réinitialiser l'interface
    resetGameUI();
    
    // Activer les boutons
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.disabled = false;
    });
    
    showToast('Game started! Get ready for the first sequence!', 'info');
}

// Fonction pour réinitialiser l'interface du jeu
function resetGameUI() {
    // Réinitialiser l'affichage
    updateScoreDisplay();
    updateRoundDisplay();
    
    // Réinitialiser les boutons
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.classList.remove('active');
        btn.disabled = false;
    });
    
    // Réinitialiser les séquences
    currentSequence = [];
    playerSequence = [];
    
    // Arrêter les timeouts
    stopRoundTimeout();
}

// Fonction pour mettre à jour l'affichage du score
function updateScoreDisplay() {
    const scoreElement = document.getElementById('globalScore');
    if (scoreElement) {
        scoreElement.textContent = globalScore;
    }
    console.log('📊 Score display updated:', globalScore);
}

// Fonction pour mettre à jour l'affichage du round
function updateRoundDisplay() {
    const roundElement = document.getElementById('currentRound');
    if (roundElement) {
        roundElement.textContent = currentRound;
    }
    console.log('🔄 Round display updated:', currentRound);
}

// Fonction pour désactiver les boutons quand éliminé
function disableButtonsOnElimination() {
    console.log('💀 Disabling buttons due to elimination');
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.disabled = true;
        btn.classList.add('eliminated');
    });
    
    // Désactiver aussi le bouton Ready
    const readyButton = document.getElementById('readyButton');
    if (readyButton) {
        readyButton.disabled = true;
        readyButton.innerHTML = '<i class="fas fa-skull me-1"></i> Eliminated';
        readyButton.className = 'btn btn-danger';
    }
    
    // Mettre à jour le statut de connexion
    const connectionStatus = document.getElementById('connectionStatus');
    if (connectionStatus) {
        connectionStatus.innerHTML = '<i class="fas fa-skull me-1"></i> Eliminated';
        connectionStatus.className = 'badge bg-danger connection-indicator';
    }
}

// Fonction pour gérer l'élimination
function handleElimination(reason) {
    if (playerEliminated) {
        console.log('💀 Player already eliminated, ignoring');
        return;
    }
    
    console.log('💀 Player eliminated:', reason);
    playerEliminated = true;
    gameState = "eliminated";
    finalScore = globalScore;
    finalRound = currentRound;
    
    // Désactiver les boutons
    disableButtonsOnElimination();
    
    // Arrêter le timeout
    stopRoundTimeout();
    
    // Afficher le message d'élimination
    showToast(`💀 You have been eliminated! Final Score: ${finalScore} | Final Round: ${finalRound}`, 'danger');
    
    // Envoyer le score final au serveur
    sendFinalScore();
    
    // Vérifier le highscore
    checkHighscore(finalScore);
}

// Fonction pour envoyer le score final au serveur
function sendFinalScore() {
    if (!playerLobbyId || !playerId) {
        console.log('⚠️ Cannot send final score: missing lobbyId or playerId');
        return;
    }
    
    const scoreData = {
        playerId: playerId,
        lobbyId: playerLobbyId,
        finalScore: finalScore,
        finalRound: finalRound,
        timestamp: Date.now()
    };
    
    console.log('📤 Sending final score:', scoreData);
    
    // Envoyer via API
    fetch(`http://localhost:80/api/players/score`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(scoreData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            console.log('✅ Final score sent successfully');
        } else {
            console.error('❌ Failed to send final score:', data.error);
        }
    })
    .catch(error => {
        console.error('❌ Error sending final score:', error);
    });
}

// Fonction pour vérifier et afficher le highscore
function checkHighscore(score) {
    fetch('http://localhost:80/api/highscores', {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            const highscores = data.data;
            const isNewHighscore = highscores.some(hs => hs.score < score);
            
            if (isNewHighscore) {
                showHighscorePopup(score, highscores);
            }
        }
    })
    .catch(error => {
        console.error('❌ Error checking highscores:', error);
    });
}

// Fonction pour afficher le popup de highscore
function showHighscorePopup(score, highscores) {
    // Créer le popup
    const popup = document.createElement('div');
    popup.className = 'highscore-popup';
    popup.innerHTML = `
        <div class="highscore-content">
            <h3>🏆 New Highscore!</h3>
            <p>Congratulations! You achieved ${score} points!</p>
            <h4>Top 10 Scores:</h4>
            <div class="highscore-list">
                ${highscores.slice(0, 10).map((hs, index) => `
                    <div class="highscore-item ${hs.score === score ? 'current-score' : ''}">
                        <span class="rank">${index + 1}</span>
                        <span class="player">${hs.playerName}</span>
                        <span class="score">${hs.score}</span>
                    </div>
                `).join('')}
            </div>
            <button onclick="this.parentElement.parentElement.remove()" class="btn btn-primary">Close</button>
        </div>
    `;
    
    // Ajouter les styles
    popup.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0,0,0,0.8);
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 10000;
    `;
    
    const content = popup.querySelector('.highscore-content');
    content.style.cssText = `
        background: white;
        padding: 2rem;
        border-radius: 10px;
        max-width: 500px;
        max-height: 80vh;
        overflow-y: auto;
        text-align: center;
    `;
    
    // Ajouter au DOM
    document.body.appendChild(popup);
}

// Fonction pour gérer les messages de jeu
function handleGameMessage(data) {
    console.log('🎮 Game message received:', data);
    
    switch (data.action) {
        case 'game_sequence':
            // Vérifier si le joueur est éliminé
            if (playerEliminated) {
                console.log('💀 Player eliminated, ignoring sequence');
                return;
            }
            
            // Initialiser le nouveau round
            currentGameId = data.gameId;
            currentRound = data.round;
            currentSequence = data.sequence;
            playerSequence = [];
            playerScore = 0; // Score du round seulement
            gameState = "playing";
            
            console.log(`🎮 Starting round ${currentRound} with sequence length: ${currentSequence.length}`);
            
            showToast(`Round ${data.round} - Follow the sequence!`, 'info');
            
            // Activer les boutons Simon seulement si pas éliminé
            document.querySelectorAll('.simon-button').forEach(btn => {
                btn.disabled = false;
            });
            
            // Afficher la séquence
            displaySequence(data.sequence);
            break;
            
        case 'player_eliminated':
            console.log('💀 Player elimination message received');
            handleEliminationMessage(data);
            break;
            
        default:
            return;
    }
}

// Fonction pour gérer les messages d'élimination reçus via MQTT
function handleEliminationMessage(data) {
    console.log('💀 Elimination message received:', data);
    
    // Vérifier si c'est pour ce joueur
    if (data.eliminatedPlayer === window.controllerId) {
        // Vérifier si le joueur n'est pas déjà éliminé
        if (playerEliminated) {
            console.log('💀 Player already eliminated, ignoring duplicate elimination message');
            return;
        }
        
        console.log('💀 YOU HAVE BEEN ELIMINATED!');
        
        // Marquer le joueur comme éliminé
        playerEliminated = true;
        gameState = "eliminated";
        
        // Sauvegarder le score et round final
        finalScore = data.finalScore || globalScore;
        finalRound = data.finalRound || currentRound;
        
        // Désactiver les boutons
        disableButtonsOnElimination();
        
        // Afficher le message d'élimination
        const reason = data.reason || "Éliminé";
        showToast(`💀 ${data.message || 'You have been eliminated!'} (${reason})`, 'danger');
        
        // Arrêter le timeout
        stopRoundTimeout();
        
        // Vérifier le highscore
        checkHighscore(finalScore);
    }
}

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



function handleSimonClick(color) {
    // Vérifier si le joueur est éliminé
    if (playerEliminated) {
    
        showToast('You are eliminated - cannot play anymore', 'warning');
        return;
    }
    
    // Vérifier si le jeu est en cours et si la séquence n'est pas en train de se jouer
    if (gameState !== "playing" || isSequencePlaying) {
        
        return;
    }
    
    
    
    // Convertir la couleur en index pour le son
    const colorIndex = getColorIndex(color);
    
    // Jouer le son
    playButtonSound(colorIndex);
    
    // Animation du bouton
    const button = document.getElementById(color + 'Button');
    if (button) {
        button.classList.add('lighting-up');
        setTimeout(() => {
            button.classList.remove('lighting-up');
        }, 600);
    }
    
    // Vérifier si le clic est correct
    const expectedColor = getColorFromIndex(currentSequence[playerSequence.length]);
    if (color !== expectedColor) {
        // Vérifier si le joueur n'est pas déjà éliminé
        if (playerEliminated) {
            console.log('💀 Player already eliminated, ignoring wrong color click');
            return;
        }
        
        // Mauvaise couleur - éliminer le joueur
        console.log('❌ Wrong color clicked, eliminating player');
        handleElimination('Wrong color');
        return;
    }
    
    // Calculer le score basé sur la vitesse
    const clickTime = Date.now();
    const reactionTime = clickTime - roundStartTime;
    const speedBonus = Math.max(0, 1000 - reactionTime); // Bonus pour rapidité
    const clickScore = 100 + speedBonus;
    playerScore += clickScore;
    globalScore += clickScore; // Ajouter au score global
    
    
    
    // Envoyer le score en temps réel au lobby
    sendScoreToLobby();
    
    // Ajouter à la séquence du joueur
    playerSequence.push(color);
    
            // Vérifier si la séquence est complète
        if (playerSequence.length >= currentSequence.length) {
           
            showToast(`🎉 Round completed! Global score: ${globalScore}`, 'success');
            
            // Ne pas incrémenter le round ici - attendre le message du serveur
            // Le round sera mis à jour quand le serveur enverra la nouvelle séquence
            
            // Envoyer le score final du round au serveur
            sendFinalScore();
        } else {
        // Mettre à jour le temps de début pour le prochain clic
        roundStartTime = Date.now();
        showToast(`✅ Correct! Global score: ${globalScore}`, 'success');
    }
    
    updateStats();
}





function toggleReadyStatus() {
    
    

    
    if (!playerLobbyId || !playerId) {
        
        showToast('En attente de sélection par un joueur...', 'info');
        return;
    }

    const newStatus = playerReady ? 'waiting' : 'ready';
    const button = document.getElementById('readyButton');
    
  
    // Appel API pour mettre à jour le statut
    fetch(`http://localhost:81/api/lobby/${playerLobbyId}/players/${playerId}/status`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            status: newStatus
        })
    })
    .then(response => {
       
        return response.json();
    })
    .then(data => {
        
        
        if (data.success) {
            // Mettre à jour la variable locale seulement si l'API réussit
            const oldPlayerReady = playerReady;
            playerReady = !playerReady;
            
            console.log('🔄 playerReady changed from', oldPlayerReady, 'to', playerReady);
            
            // Mettre à jour l'interface utilisateur
            updateReadyButton();
            
            if (playerReady) {
                showToast('✅ Statut changé à Ready !', 'success');
            } else {
                showToast('⏳ Statut changé à Waiting.', 'info');
            }
            
            
            
            // Récupérer le statut actuel depuis la base de données pour synchroniser
            setTimeout(() => {
                refreshPlayerStatus();
            }, 500);
        } else {
           
            showToast('❌ Erreur lors du changement de statut: ' + data.error, 'danger');
            
           
        }
    })
    .catch(error => {
      
        showToast('❌ Erreur de connexion lors du changement de statut.', 'danger');
    });
}

function updateStats() {
    document.getElementById('statRound').textContent = currentRound;
    document.getElementById('statScore').textContent = globalScore; // Afficher le score global
    document.getElementById('statTime').textContent = formatTime(gameTimer);
    document.getElementById('currentRound').textContent = currentRound;
    document.getElementById('roundProgress').textContent = `${playerSequence.length}/${currentSequence.length}`;
    
    if (gameState === "playing") {
        const progress = (playerSequence.length / currentRound) * 100;
        document.getElementById('progressBar').style.width = progress + '%';
    }
}

function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}





function showToast(message, type) {
    const toastClass = type === 'success' ? 'alert-success' : 
                     type === 'danger' ? 'alert-danger' : 
                     type === 'info' ? 'alert-info' : 'alert-warning';
    
    const toast = document.createElement('div');
    toast.className = `alert ${toastClass} position-fixed top-0 end-0 m-3`;
    toast.style.zIndex = '9999';
    toast.textContent = message;
    document.body.appendChild(toast);
    
    setTimeout(() => {
        toast.remove();
    }, 3000);
}

// Initialize
createBubbles();
updateStats();
initMqtt();

// Le controller attend maintenant d'être sélectionné par un joueur
// au lieu de chercher automatiquement les lobbies

// Handle window resize - No longer needed with CSS media queries
// window.addEventListener('resize', updateButtonSizes);

// Create controller instance via MQTT
function createControllerInstance() {
    console.log('🔧 Creating controller instance...');
    
    if (!mqttClient || !mqttClient.connected) {
        console.log('❌ MQTT not connected. Retrying...');
        showToast('MQTT not connected. Retrying...', 'warning');
        setTimeout(createControllerInstance, 1000);
        return;
    }

    const message = {
        timestamp: Date.now(),
        action: 'create'
    };

    console.log('📤 Publishing controller creation message:', message);
    mqttClient.publish('group-05/controllers/create', JSON.stringify(message));
    console.log('✅ Controller creation message sent');
}




// Vérifier si ce controller a été sélectionné par un joueur
function checkControllerSelection() {
    

 
    
    fetch(`http://localhost:81/api/controller/${window.controllerId}/info`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        if (data.success && data.data) {
            
            
            // Mettre à jour les variables globales
            playerLobbyId = data.data.lobbyId;
            playerId = data.data.playerId;
            playerReady = data.data.status === 'ready';
            
            // Mettre à jour l'affichage du lobby
            const connectionStatus = document.getElementById('connectionStatus');
            if (connectionStatus) {
                connectionStatus.innerHTML = `<i class="fas fa-wifi me-1"></i>Lobby #${data.data.lobbyId}`;
            }
            
            // Mettre à jour l'affichage du nom du joueur
            const playerNameElement = document.getElementById('playerName');
            if (playerNameElement) {
                playerNameElement.textContent = `${data.data.username} (ID: ${data.data.playerId})`;
            }
            
            // S'abonner au topic de jeu spécifique à ce controller
            if (mqttClient && mqttClient.connected && window.controllerId) {
                mqttClient.subscribe(`group-05/controller/${window.controllerId}/game`);
                console.log(`🎮 Subscribed to controller game topic: group-05/controller/${window.controllerId}/game`);
            }
            // S'abonner aussi au topic du lobby pour round_end / game_end
            if (mqttClient && mqttClient.connected && playerLobbyId) {
                mqttClient.subscribe(`group-05/lobby/${playerLobbyId}/game`);
                console.log(`🛎 Subscribed to lobby game topic: group-05/lobby/${playerLobbyId}/game`);
            }
            
            // Mettre à jour le bouton
            updateReadyButton();
            
            // Récupérer le statut actuel depuis la base de données
            setTimeout(() => {
                refreshPlayerStatus();
            }, 1000);
            
            showToast(`Selected by ${data.data.username} (ID: ${data.data.playerId})! Connected to Lobby #${data.data.lobbyId}`, 'success');
            
            // Arrêter la vérification périodique
            if (window.selectionCheckInterval) {
                clearInterval(window.selectionCheckInterval);
                window.selectionCheckInterval = null;
            }
        } else {
           
            // Continuer à vérifier périodiquement
            if (!window.selectionCheckInterval) {
                window.selectionCheckInterval = setInterval(checkControllerSelection, 2000); // Vérifier toutes les 2 secondes
            }
        }
    })
    .catch(error => {
        
        // Continuer à vérifier périodiquement même en cas d'erreur
        if (!window.selectionCheckInterval) {
            window.selectionCheckInterval = setInterval(checkControllerSelection, 2000);
        }
    });
}

// Initialiser la connexion MQTT
function initMqtt() {
    console.log('🔧 Initializing MQTT connection...');
    
    function connectToMQTT() {
        const options = {
            username: "group-05",
            password: "X8y!dF5zLp2A",
            reconnectPeriod: 3000,
            connectTimeout: 4000,
            clean: true,
            clientId: "controller-" + Math.random().toString(16).substr(2, 8)
        };

        mqttClient = mqtt.connect("ws://iti-mqtt.mni.thm.de:9001", options);

        mqttClient.on("connect", () => {
            
            showToast('Connected to MQTT broker', 'success');
            
            // S'abonner aux topics des contrôleurs
            mqttClient.subscribe('group-05/controllers/created');
            
            mqttClient.subscribe('group-05/controllers/deleted');
            
            mqttClient.subscribe('group-05/controllers/selected');
            
            mqttClient.subscribe('group-05/players/status');
            
            mqttClient.subscribe('group-05/game/elimination');
            

            
            // Créer le contrôleur une fois connecté
            setTimeout(createControllerInstance, 500);
        });

        mqttClient.on("error", err => {
            showToast('MQTT connection error: ' + err.message, 'danger');
        });

        mqttClient.on("offline", () => {
            showToast('MQTT connection offline', 'warning');
        });

        mqttClient.on('message', (topic, message) => {
            console.log('📨 MQTT message received - Topic:', topic, 'Message:', message.toString());
            
            handleMqttMessage(topic, message);
        });

        mqttClient.on('close', (error) => {
            
            showToast('MQTT connection closed', 'warning');
        });

        mqttClient.on('reconnect', () => {
            showToast('Reconnecting to MQTT...', 'info');
        });
    }

    // Connecter au MQTT VPN THM
    connectToMQTT();
}

    // Gérer les messages MQTT reçus
    function handleMqttMessage(topic, message) {
        console.log('📨 MQTT message received:', topic, message.toString());
        
        try {
            const data = JSON.parse(message.toString());
            console.log('📨 Parsed MQTT data:', data);
            
            console.log('🔍 Processing topic:', topic);
            console.log('🔍 Current controllerId:', window.controllerId);
            console.log('🔍 Current playerLobbyId:', playerLobbyId);
        
        switch (topic) {
            case 'group-05/controllers/created':
                console.log('🎮 Controller creation response received:', data);
                
                if (data.controller && data.controller.id) {
                    window.controllerId = data.controller.id;
                    console.log('✅ Controller ID set to:', data.controller.id);
                    
                    // Update the connection status to show controller ID (will be updated to lobby ID later)
                    const connectionStatus = document.getElementById('connectionStatus');
                    connectionStatus.innerHTML = `<i class="fas fa-wifi me-1"></i>Lobby #-`;
                    
                    // Update the player name to show controller ID
                    const playerNameElement = document.getElementById('playerName');
                    const controllerIdElement = document.getElementById('controllerId');
                    if (playerNameElement && controllerIdElement) {
                        playerNameElement.textContent = `Player #${data.controller.id}`;
                        controllerIdElement.textContent = data.controller.id;
                    }
                    
                    // S'abonner IMMÉDIATEMENT au topic de jeu pour ce controller
                    if (mqttClient && mqttClient.connected) {
                        mqttClient.subscribe(`group-05/controller/${window.controllerId}/game`);
                        console.log(`🎮 IMMEDIATE subscription to controller game topic: group-05/controller/${window.controllerId}/game`);
                    }
                    
                    showToast(`Controller #${data.controller.id} created successfully via MQTT`, 'success');
                    
                    // Vérifier périodiquement si ce controller a été sélectionné par un joueur
                    checkControllerSelection();
                } else {
                    console.log('❌ Invalid controller data received:', data);
                }
                break;
            case 'group-05/controllers/deleted':
                console.log('Controller deleted via MQTT:', data);
                if (data.controllerId) {
                    showToast(`Controller #${data.controllerId} deleted successfully via MQTT`, 'info');
                }
                break;

            case 'group-05/controllers/selected':
               
                if (data.controllerId && data.controllerId === window.controllerId) {
                    
                    
                    playerLobbyId = data.lobbyId;
                    playerId = data.playerId;
                    
                    // Mettre à jour l'affichage du lobby
                    const connectionStatus = document.getElementById('connectionStatus');
                    if (connectionStatus) {
                        connectionStatus.innerHTML = `<i class="fas fa-wifi me-1"></i>Lobby #${data.lobbyId}`;
                    }
                    
                    // S'abonner au topic de jeu spécifique à ce controller
                    if (mqttClient && mqttClient.connected && window.controllerId) {
                        mqttClient.subscribe(`group-05/controller/${window.controllerId}/game`);
                        console.log(`🎮 Subscribed to controller game topic: group-05/controller/${window.controllerId}/game`);
                    }
                    
                    // S'abonner aussi au topic du lobby pour recevoir les séquences
                    if (mqttClient && mqttClient.connected && playerLobbyId) {
                        mqttClient.subscribe(`group-05/lobby/${playerLobbyId}/game`);
                        console.log(`🛎 Subscribed to lobby game topic: group-05/lobby/${playerLobbyId}/game`);
                    }
                    
                    showToast(`Selected by player! Connected to Lobby #${data.lobbyId}`, 'success');
                    
                    // Arrêter la vérification périodique
                    if (window.selectionCheckInterval) {
                        clearInterval(window.selectionCheckInterval);
                        window.selectionCheckInterval = null;
                    }
                }
                break;

            case 'group-05/players/status':
               
                

                
                if (data.action === 'player_status_updated' && data.playerId === playerId) {
                   
                    
                    // Mettre à jour le statut local
                    playerReady = data.status === 'ready';
                    
                   
                    
                    // Mettre à jour l'interface utilisateur
                    updateReadyButton();
                    
                  
                    showToast(`Status updated to: ${data.status}`, 'info');
                } 
                break;
            case `group-05/controller/${window.controllerId}/game`:
                console.log('🎯 CONTROLLER GAME TOPIC MATCHED! Processing sequence...');
                handleGameMessage(data);
                break;
            case `group-05/lobby/${playerLobbyId}/game`:
                console.log('🎯 LOBBY GAME TOPIC MATCHED! Processing sequence...');
                handleGameMessage(data);
                break;
                
            case 'group-05/game/elimination':
                handleEliminationMessage(data);
                break;
            default:
                return;
        }
    } catch (error) {
        console.error('❌ Error parsing MQTT message:', error);
        console.error('📨 Raw message:', message.toString());
    }
}

// Afficher la séquence en faisant clignoter les boutons
function displaySequence(sequence) {
    const colors = ['red', 'green', 'yellow', 'blue'];
    const buttonIds = ['redButton', 'greenButton', 'yellowButton', 'blueButton'];
    const sequenceText = sequence.map(index => colors[index]).join(' → ');
    
    
    // Désactiver les boutons pendant l'animation
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.disabled = true;
    });
    
    // Marquer que la séquence est en cours de lecture
    isSequencePlaying = true;
    
    // Animer la séquence
    let currentIndex = 0;
    const animationInterval = setInterval(() => {
        if (currentIndex >= sequence.length) {
            // Séquence terminée, activer le jeu
            isSequencePlaying = false;
            roundStartTime = Date.now(); // Démarrer le chronomètre pour le premier clic
            
            // Démarrer le timeout du round
            startRoundTimeout();
            
            // Réactiver les boutons Simon seulement si le joueur n'est pas éliminé
            if (!playerEliminated) {
                document.querySelectorAll('.simon-button').forEach(btn => {
                    btn.disabled = false;
                });
                
                
                showToast('🎮 Your turn! Click the buttons in the correct order!', 'success');
            } else {
              
                showToast('You are eliminated - watching from sidelines', 'warning');
            }
            
            clearInterval(animationInterval);
            return;
        }
        
        const colorIndex = sequence[currentIndex];
        
        // Essayer d'abord avec data-color, puis avec l'ID
        let button = document.querySelector(`[data-color="${colorIndex}"]`);
        if (!button && colorIndex >= 0 && colorIndex < buttonIds.length) {
            button = document.getElementById(buttonIds[colorIndex]);
        }
        
        if (button) {
           
            // Jouer le son
            playButtonSound(colorIndex);
            
            // Ajouter la classe d'animation
            button.classList.add('lighting-up');
            
            // Retirer la classe après l'animation
            setTimeout(() => {
                button.classList.remove('lighting-up');
            }, 600);
        } 
        
        currentIndex++;
    }, 800); // 800ms entre chaque bouton
}

// Convertir la couleur en index
function getColorIndex(color) {
    const colorMap = {
        'red': 0,
        'green': 1,
        'yellow': 2,
        'blue': 3
    };
    return colorMap[color] || 0;
}

// Convertir l'index en couleur
function getColorFromIndex(index) {
    const colors = ['red', 'green', 'yellow', 'blue'];
    return colors[index] || 'red';
}

// Envoyer le score final au serveur
function sendFinalScore() {
    if (!currentGameId || !playerLobbyId || !playerId) {
       
        return;
    }
    
    const scoreData = {
        gameId: currentGameId,
        lobbyId: playerLobbyId,
        playerId: playerId,
        score: globalScore, // Envoyer le score global
        roundScore: playerScore, // Score du round actuel
        eliminated: playerEliminated,
        sequenceCompleted: !playerEliminated && playerSequence.length >= currentSequence.length
    };
    
    console.log('📤 Sending final score:', scoreData);
    
    // Mettre à jour le score dans la base de données
    fetch(`http://localhost:80/api/lobby/${playerLobbyId}/players/${playerId}/score`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            score: globalScore,
            round: currentRound
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            console.log('✅ Final score updated in database:', globalScore, 'Round:', currentRound);
        } else {
            console.error('❌ Failed to update final score in database:', data.error);
        }
    })
    .catch(error => {
        console.error('❌ Error updating final score in database:', error);
    });
    
    // Envoyer via MQTT
    if (mqttClient && mqttClient.connected) {
        const message = JSON.stringify({
            action: 'player_score',
            ...scoreData,
            timestamp: Date.now()
        });
        
        mqttClient.publish(`group-05/lobby/${playerLobbyId}/game/score`, message);
        
    }
    
    // Réinitialiser pour le prochain round
    resetGameState();
}

// Réinitialiser l'état du jeu
function resetGameState() {
    gameState = "waiting";
    isSequencePlaying = false;
    playerEliminated = false;
    playerSequence = [];
    currentSequence = [];
    playerScore = 0; // Score du round seulement
    
   
    
    // Désactiver les boutons Simon
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.disabled = true;
    });
    
  
}



// Réinitialiser l'interface utilisateur pour un nouveau jeu
function resetGameUI() {
    
    
    // Afficher à nouveau la zone de jeu
    const gameCard = document.querySelector('.game-card');
    if (gameCard) {
        gameCard.style.display = 'block';
    }
    
    // Supprimer l'écran d'élimination s'il existe
    const eliminationScreen = document.getElementById('eliminationScreen');
    if (eliminationScreen) {
        eliminationScreen.remove();
    }
    
    // Réactiver tous les boutons Simon
    document.querySelectorAll('.simon-button').forEach(btn => {
        btn.disabled = false;
        btn.style.opacity = '1';
        btn.style.cursor = 'pointer';
    });
    
    // Réinitialiser le statut de connexion
    const connectionStatus = document.getElementById('connectionStatus');
    if (connectionStatus) {
        connectionStatus.innerHTML = `<i class="fas fa-wifi me-1"></i>Lobby #${playerLobbyId || '-'}`;
        connectionStatus.className = 'badge bg-success connection-indicator';
    }
    
    // Réinitialiser le bouton Ready
    const readyButton = document.getElementById('readyButton');
    if (readyButton) {
        readyButton.innerHTML = '<i class="fas fa-clock me-1"></i> Waiting';
        readyButton.className = 'btn btn-outline-warning';
        readyButton.disabled = false;
    }
    
    
}



// Jouer le son pour chaque bouton
function playButtonSound(colorIndex) {
   
        // Créer un contexte audio si pas encore fait
        if (!window.audioContext) {
            window.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        }
        
        // Fréquences pour chaque couleur (notes musicales)
        const frequencies = {
            0: 440,  // Rouge - La
            1: 494,  // Vert - Si
            2: 523,  // Jaune - Do
            3: 587   // Bleu - Ré
        };
        
        const frequency = frequencies[colorIndex];
        if (!frequency) return;
        
        // Créer un oscillateur
        const oscillator = window.audioContext.createOscillator();
        const gainNode = window.audioContext.createGain();
        
        oscillator.connect(gainNode);
        gainNode.connect(window.audioContext.destination);
        
        // Configurer le son
        oscillator.frequency.setValueAtTime(frequency, window.audioContext.currentTime);
        oscillator.type = 'sine';
        
        // Enveloppe du son (attack, decay, sustain, release)
        gainNode.gain.setValueAtTime(0, window.audioContext.currentTime);
        gainNode.gain.linearRampToValueAtTime(0.3, window.audioContext.currentTime + 0.01);
        gainNode.gain.exponentialRampToValueAtTime(0.01, window.audioContext.currentTime + 0.3);
        
        // Démarrer et arrêter le son
        oscillator.start(window.audioContext.currentTime);
        oscillator.stop(window.audioContext.currentTime + 0.3);
        
    
}

// Récupérer le statut actuel du joueur depuis la base de données
function refreshPlayerStatus() {
    if (!playerLobbyId || !playerId) {
       
        return;
    }
    
    
    
    fetch(`http://localhost:81/api/lobby/${playerLobbyId}/players/${playerId}/status`, {
        method: 'GET',
        headers: {
            'Content-Type': 'application/json',
        }
    })
    .then(response => response.json())
    .then(data => {
        
        
        if (data.success && data.data) {
           
            
            const newStatus = data.data.status;
            const oldPlayerReady = playerReady;
            playerReady = newStatus === 'ready';
            
            
            
            // Mettre à jour l'interface seulement si le statut a changé
            if (oldPlayerReady !== playerReady) {
                
                updateReadyButton();
            } 
        } 
    })
    .catch(error => {
        console.error('❌ Error refreshing player status:', error);
    });
}

// Mettre à jour l'apparence du bouton selon le statut
function updateReadyButton() {
    
    const button = document.getElementById('readyButton');
    if (!button) {
        console.error('❌ Ready button not found!');
        return;
    }
    
   
    
    if (playerReady) {
        
        button.innerHTML = '<i class="fas fa-check me-1"></i> Ready';
        button.className = 'btn btn-success';
        
    } else {
       
        button.innerHTML = '<i class="fas fa-clock me-1"></i> Waiting';
        button.className = 'btn btn-outline-warning';
       
    }
    
    console.log('🎨 updateReadyButton() completed');
}

// Gérer les messages d'élimination reçus via MQTT
function handleEliminationMessage(data) {
    console.log('💀 Elimination message received:', data);
    
    // Vérifier si c'est pour ce joueur
    if (data.eliminatedPlayer === window.controllerId) {
        // Vérifier si le joueur n'est pas déjà éliminé
        if (playerEliminated) {
            console.log('💀 Player already eliminated, ignoring duplicate elimination message');
            return;
        }
        
        console.log('💀 YOU HAVE BEEN ELIMINATED!');
        
        // Marquer le joueur comme éliminé
        playerEliminated = true;
        
        // Sauvegarder le score et round final
        finalScore = data.finalScore || globalScore;
        finalRound = data.finalRound || currentRound;
        
        // Mettre à jour l'interface pour l'état éliminé
        updateEliminatedUI();
        
        // Afficher le message d'élimination
        const reason = data.reason || "Éliminé";
        showToast(`💀 ${data.message || 'You have been eliminated!'} (${reason})`, 'danger');
        
        // Arrêter le timeout
        stopRoundTimeout();
        
        // Désactiver tous les boutons
        document.querySelectorAll('.simon-button').forEach(btn => {
            btn.disabled = true;
        });
    }
}

// Mettre à jour l'interface pour l'état éliminé
function updateEliminatedUI() {
    console.log('🎨 Updating UI for eliminated state');
    
    // Changer le titre du jeu
    const gameTitle = document.querySelector('.card-title');
    if (gameTitle) {
        gameTitle.innerHTML = '<i class="fas fa-skull me-2"></i>Game Over - Eliminated';
    }
    
    // Mettre à jour les statistiques finales
    document.getElementById('statScore').textContent = finalScore || globalScore;
    document.getElementById('statRound').textContent = finalRound || currentRound;
    document.getElementById('statTime').textContent = formatTime(gameTimer);
    
    // Changer le texte du bouton de jeu
    const gameButton = document.querySelector('.btn-outline-warning');
    if (gameButton) {
        gameButton.innerHTML = '<i class="fas fa-trophy me-2"></i>Final Score';
        gameButton.className = 'btn btn-outline-danger w-100 py-3';
    }
    
    // Ajouter un message d'élimination
    const gameCard = document.querySelector('.game-card');
    if (gameCard) {
        const eliminationMessage = document.createElement('div');
        eliminationMessage.className = 'alert alert-danger text-center mb-3';
        eliminationMessage.innerHTML = `
            <i class="fas fa-skull me-2"></i>
            <strong>You have been eliminated!</strong><br>
            Final Score: ${finalScore || globalScore} | Final Round: ${finalRound || currentRound}
        `;
        
        // Insérer le message après le header de la carte
        const cardHeader = gameCard.querySelector('.card-header');
        if (cardHeader) {
            cardHeader.parentNode.insertBefore(eliminationMessage, cardHeader.nextSibling);
        }
    }
    
    console.log('🎨 Eliminated UI updated');
}

// Variables pour suivre l'état d'élimination (déclarées au début du fichier)

// Fonction pour envoyer le score en temps réel au lobby
function sendScoreToLobby() {
    if (!playerLobbyId || !playerId) {
        
        return;
    }
    
    // Envoyer le score à la base de données via API
    fetch(`http://localhost:80/api/lobby/${playerLobbyId}/players/${playerId}/score`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({
            score: globalScore,
            round: currentRound
        })
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            console.log('✅ Score updated in database:', globalScore, 'Round:', currentRound);
        } else {
            console.error('❌ Failed to update score in database:', data.error);
        }
    })
    .catch(error => {
        console.error('❌ Error updating score in database:', error);
    });
    
    // Envoyer via MQTT pour la synchronisation en temps réel
    if (typeof mqtt !== 'undefined' && mqttClient) {
        const scoreMessage = {
            action: 'player_score_updated',
            playerId: playerId,
            lobbyId: playerLobbyId,
            score: globalScore,
            currentRound: currentRound,
            timestamp: Date.now()
        };
        
        mqttClient.publish('group-05/players/score', JSON.stringify(scoreMessage));
    }
    
    // Mettre à jour l'affichage local
    updateScoreDisplay();
}

// Mettre à jour l'affichage du score
function updateScoreDisplay() {
    const scoreElement = document.getElementById('globalScore');
    if (scoreElement) {
        scoreElement.textContent = globalScore;
    }
    console.log('📊 Score display updated:', globalScore);
}

// Mettre à jour l'affichage du round
function updateRoundDisplay() {
    const roundElement = document.getElementById('currentRound');
    if (roundElement) {
        roundElement.textContent = currentRound;
    }
    console.log('🔄 Round display updated:', currentRound);
}

// Fonction pour envoyer la réponse de séquence au backend
function sendSequenceResponse(sequence, responseTime) {
    if (!window.controllerId || !playerLobbyId) {
        console.error('❌ Cannot send sequence response: missing controllerId or lobbyId');
        return;
    }
    
    const requestData = {
        gameId: parseInt(playerLobbyId),
        controllerId: window.controllerId,
        sequence: sequence,
        responseTime: responseTime
    };
    
    console.log('📤 Sending sequence response to backend:', requestData);
    
    fetch('/api/game/sequence-response', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestData)
    })
    .then(response => response.json())
    .then(data => {
        console.log('📥 Sequence response result:', data);
        
        if (data.success) {
            if (data.correct) {
                // ✅ Bonne réponse
                console.log('✅ Sequence correct! Score:', data.score);
                
                // Ajouter le score du round au score global
                globalScore += data.score;
                console.log('📊 Updated global score:', globalScore);
                
                // Mettre à jour l'affichage du score
                updateScoreDisplay();
                
                showToast(`✅ Correct! +${data.score} points`, 'success');
                
                // Continuer avec la séquence suivante
                currentRound++;
                console.log('🔄 Moving to round:', currentRound);
                
                // Mettre à jour l'affichage du round
                updateRoundDisplay();
                
                // Continuer le jeu (le backend enverra la prochaine séquence)
                // Ne pas réinitialiser les séquences ici
            } else if (data.eliminated) {
                // ❌ Mauvaise réponse - élimination
                console.log('❌ Wrong sequence - eliminated');
                handleElimination('Wrong sequence');
            }
        } else {
            console.error('❌ Sequence response failed:', data.message);
            showToast('❌ Error processing sequence', 'danger');
        }
    })
    .catch(error => {
        console.error('❌ Error sending sequence response:', error);
        showToast('❌ Network error', 'danger');
    });
}


// Fonction pour démarrer le timeout du round
function startRoundTimeout() {
    // Annuler le timeout précédent s'il existe
    if (roundTimeout) {
        clearTimeout(roundTimeout);
    }
    
   
    
    roundTimeout = setTimeout(() => {
        if (gameState === "playing" && !playerEliminated) {
            // Vérifier si le joueur n'est pas déjà éliminé
            if (playerEliminated) {
                console.log('💀 Player already eliminated, ignoring timeout elimination');
                return;
            }
            
            console.log('⏰ Timeout reached, eliminating player');
            handleElimination('Timeout');
        }
    }, 5000); // 5 secondes pour compléter la séquence
}

// Fonction pour arrêter le timeout du round
function stopRoundTimeout() {
    if (roundTimeout) {
        clearTimeout(roundTimeout);
        roundTimeout = null;
       
    }
}

// Fonction pour envoyer l'élimination au lobby
function sendEliminationToLobby(reason) {
    if (!playerLobbyId || !playerId) {
        
        return;
    }
    
    // Vérifier si on a déjà envoyé un message d'élimination
    if (window.eliminationSent) {
        console.log('💀 Elimination message already sent, skipping');
        return;
    }
    
    // Marquer qu'on a envoyé un message d'élimination
    window.eliminationSent = true;
    
    
    
    // Envoyer via MQTT
    if (typeof mqtt !== 'undefined' && mqttClient) {
        const eliminationMessage = {
            action: 'player_eliminated',
            playerId: playerId,
            lobbyId: playerLobbyId,
            reason: reason,
            finalScore: globalScore,
            finalRound: currentRound,
            timestamp: Date.now()
        };
        
        mqttClient.publish('group-05/game/elimination', JSON.stringify(eliminationMessage));
    }
}

// Fonction pour reconnecter au MQTT (appelée depuis le HTML)
function reconnect() {
    
    
    // Masquer l'avertissement d'inactivité
    const inactivityWarning = document.getElementById('inactivityWarning');
    if (inactivityWarning) {
        inactivityWarning.classList.add('d-none');
    }
    
    // Reconnecter au MQTT si le client existe
    if (mqttClient) {
        try {
            
            showToast('🔄 Reconnecting to MQTT...', 'info');
        } catch (error) {
            
            showToast('❌ Reconnection failed', 'danger');
        }
    } else {
        // Si pas de client MQTT, réinitialiser la connexion
        initMqtt();
        showToast('🔄 Initializing new MQTT connection...', 'info');
    }
}

// Fonction pour supprimer le controller de la base de données
function deleteControllerFromDatabase() {
    if (!window.controllerId) {
        console.log('⚠️ No controller ID to delete');
        return;
    }
    
    console.log(`🗑️ Deleting controller ${window.controllerId} from database...`);
    
    const data = JSON.stringify({});
    
    // Utiliser sendBeacon pour une suppression plus fiable lors de la fermeture
    if (navigator.sendBeacon) {
        navigator.sendBeacon(`http://localhost:80/api/controller/${window.controllerId}/delete`, data);
        console.log(`✅ Controller deletion request sent via sendBeacon`);
    } else {
        // Fallback avec fetch synchrone si sendBeacon n'est pas supporté
        const xhr = new XMLHttpRequest();
        xhr.open('DELETE', `http://localhost:80/api/controller/${window.controllerId}/delete`, false);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send(data);
        console.log(`✅ Controller deletion request sent via XHR`);
    }
}

// Fonction pour nettoyer les ressources avant la fermeture
function cleanupOnClose() {
    console.log('🧹 Cleaning up controller resources...');
    
    // Arrêter les timeouts
    if (roundTimeout) {
        clearTimeout(roundTimeout);
        roundTimeout = null;
    }
    
    // Arrêter les intervalles
    if (window.selectionCheckInterval) {
        clearInterval(window.selectionCheckInterval);
        window.selectionCheckInterval = null;
    }
    
    // Déconnecter du MQTT
    if (mqttClient && mqttClient.connected) {
        console.log('📡 Disconnecting from MQTT...');
        mqttClient.end();
    }
    
    // Supprimer le controller de la base de données
    deleteControllerFromDatabase();
}

// Event listener pour la fermeture de la fenêtre
window.addEventListener('beforeunload', function(event) {
    console.log('🚪 Window closing, cleaning up...');
    cleanupOnClose();
});

// Event listener pour la fermeture de l'onglet
window.addEventListener('unload', function(event) {
    console.log('📑 Tab closing, cleaning up...');
    cleanupOnClose();
});

// Event listener pour la fermeture de la page (navigation)
window.addEventListener('pagehide', function(event) {
    console.log('📄 Page hiding, cleaning up...');
    cleanupOnClose();
});


