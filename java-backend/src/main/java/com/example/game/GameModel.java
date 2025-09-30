package com.example.game;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class GameModel {
    private int gameId;
    private int lobbyId;
    private GameState state;
    private List<String> colors;
    private List<Integer> currentSequence;
    private int currentRound;
    private int maxRounds;
    private int sequenceLength;
    private long gameStartTime;
    private long roundStartTime;
    private int roundDuration; // en secondes
    
    public enum GameState {
        WAITING,    // En attente que tous les joueurs soient prêts
        STARTING,   // Le jeu va commencer
        PLAYING,    // Le jeu est en cours
        ROUND_END,  // Fin d'un round
        GAME_END    // Fin du jeu
    }
    
    public GameModel(int lobbyId) {
        this.lobbyId = lobbyId;
        this.state = GameState.WAITING;
        this.currentRound = 0;
        this.maxRounds = 5;
        this.sequenceLength = 3;
        this.roundDuration = 30;
        
        // Initialiser les couleurs disponibles
        this.colors = new ArrayList<>();
        colors.add("red");
        colors.add("green");
        colors.add("yellow");
        colors.add("blue");
        
        this.currentSequence = new ArrayList<>();
    }
    
    // Générer une nouvelle séquence pour le round actuel
    public void generateNewSequence() {
        currentSequence.clear();
        Random random = new Random();
        
        // La longueur de la séquence augmente avec le round actuel (pas le suivant)
        // Round 1: 3 éléments, Round 2: 4 éléments, etc.
        int sequenceLength = Math.min(2 + currentRound, 8);
        
        for (int i = 0; i < sequenceLength; i++) {
            int colorIndex = random.nextInt(colors.size());
            currentSequence.add(colorIndex);
        }
    }
    
    // Vérifier si tous les joueurs sont prêts
    public boolean areAllPlayersReady(List<PlayerInfo> players) {
        if (players.isEmpty()) {
            return false;
        }
        
        return players.stream().allMatch(player -> "ready".equals(player.getStatus()));
    }
    
    // Démarrer le jeu
    public void startGame() {
        this.state = GameState.STARTING;
        this.gameStartTime = System.currentTimeMillis();
        this.currentRound = 1;
        generateNewSequence();
    }
    
    // Démarrer un nouveau round
    public void startNewRound() {
        this.currentRound++;
        this.state = GameState.PLAYING;
        this.roundStartTime = System.currentTimeMillis();
        // Générer la séquence pour le nouveau round
        generateNewSequence();
    }
    
    // Vérifier si le temps du round est écoulé
    public boolean isRoundTimeExpired() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = (currentTime - roundStartTime) / 1000;
        return elapsedTime >= roundDuration;
    }
    
    // Vérifier si le jeu est terminé
    public boolean isGameFinished() {
        return currentRound > maxRounds;
    }
    
    // Getters et Setters
    public int getGameId() { return gameId; }
    public void setGameId(int gameId) { this.gameId = gameId; }
    
    public int getLobbyId() { return lobbyId; }
    public void setLobbyId(int lobbyId) { this.lobbyId = lobbyId; }
    
    public GameState getState() { return state; }
    public void setState(GameState state) { this.state = state; }
    
    public List<String> getColors() { return colors; }
    public void setColors(List<String> colors) { this.colors = colors; }
    
    public List<Integer> getCurrentSequence() { return currentSequence; }
    public void setCurrentSequence(List<Integer> currentSequence) { this.currentSequence = currentSequence; }
    
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    
    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
    
    public int getSequenceLength() { return sequenceLength; }
    public void setSequenceLength(int sequenceLength) { this.sequenceLength = sequenceLength; }
    
    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long gameStartTime) { this.gameStartTime = gameStartTime; }
    
    public long getRoundStartTime() { return roundStartTime; }
    public void setRoundStartTime(long roundStartTime) { this.roundStartTime = roundStartTime; }
    
    public int getRoundDuration() { return roundDuration; }
    public void setRoundDuration(int roundDuration) { this.roundDuration = roundDuration; }
    
    // Classe interne pour les informations des joueurs
    public static class PlayerInfo {
        private int playerId;
        private String username;
        private String status;
        private int controllerId;
        private int score;
        
        public PlayerInfo(int playerId, String username, String status, int controllerId) {
            this.playerId = playerId;
            this.username = username;
            this.status = status;
            this.controllerId = controllerId;
            this.score = 0;
        }
        
        // Getters et Setters
        public int getPlayerId() { return playerId; }
        public void setPlayerId(int playerId) { this.playerId = playerId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public int getControllerId() { return controllerId; }
        public void setControllerId(int controllerId) { this.controllerId = controllerId; }
        
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
    }
}
