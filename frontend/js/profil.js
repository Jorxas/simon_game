// Variables globales
let currentUser = null;
let selectedAvatar = null;
let toast = null;

// Initialisation au chargement de la page
document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Page de profil initialisée');
    
    // Initialiser le toast Bootstrap
    toast = new bootstrap.Toast(document.getElementById('toast'));
    
    // Charger les informations de l'utilisateur
    loadUserProfile();
    
    // Générer la grille d'avatars
    generateAvatarGrid();
    
    // Gérer la soumission du formulaire
    document.getElementById('profileForm').addEventListener('submit', handleProfileUpdate);
    
    // Gérer la validation du mot de passe
    document.getElementById('newPassword').addEventListener('input', validatePassword);
    document.getElementById('confirmPassword').addEventListener('input', validatePassword);
});

// Charger le profil de l'utilisateur
async function loadUserProfile() {
    try {
        console.log('📥 Chargement du profil utilisateur...');
        
        // Récupérer le username depuis l'URL ou le localStorage
        const urlParams = new URLSearchParams(window.location.search);
        const username = urlParams.get('username') || localStorage.getItem('currentUsername');
        
        if (!username) {
            showToast('❌ Aucun utilisateur connecté', 'Erreur', 'error');
            return;
        }
        
        // Appel API pour récupérer le profil
        const response = await fetch(`/api/me?username=${encodeURIComponent(username)}`);
        
        if (!response.ok) {
            throw new Error(`Erreur HTTP: ${response.status}`);
        }
        
        const userData = await response.json();
        currentUser = userData;
        
        console.log('✅ Profil chargé:', userData);
        
        // Mettre à jour l'interface
        updateProfileDisplay(userData);
        
    } catch (error) {
        console.error('❌ Erreur lors du chargement du profil:', error);
        showToast('❌ Erreur lors du chargement du profil', error.message, 'error');
    }
}

// Mettre à jour l'affichage du profil
function updateProfileDisplay(userData) {
    // Mettre à jour le nom d'utilisateur
    document.getElementById('currentUsername').textContent = userData.username;
    
    // Mettre à jour l'avatar actuel
    const currentAvatar = document.getElementById('currentAvatar');
    if (userData.profileUrl) {
        currentAvatar.src = userData.profileUrl;
        selectedAvatar = userData.profileUrl;
    } else {
        // Avatar par défaut
        currentAvatar.src = '/profil/avatar1.svg';
        selectedAvatar = '/profil/avatar1.svg';
    }
    
    // Marquer l'avatar sélectionné dans la grille
    updateAvatarSelection();
}

// Générer la grille d'avatars
function generateAvatarGrid() {
    const avatarGrid = document.getElementById('avatarGrid');
    avatarGrid.innerHTML = '';
    
    // Créer 10 options d'avatars
    for (let i = 1; i <= 10; i++) {
        const avatarOption = document.createElement('div');
        avatarOption.className = 'avatar-option';
        avatarOption.dataset.avatar = `/profil/avatar${i}.svg`;
        
        const avatarImg = document.createElement('img');
        avatarImg.src = `/profil/avatar${i}.svg`;
        avatarImg.alt = `Avatar ${i}`;
        
        avatarOption.appendChild(avatarImg);
        
        // Gestionnaire de clic
        avatarOption.addEventListener('click', function() {
            selectAvatar(this.dataset.avatar);
        });
        
        avatarGrid.appendChild(avatarOption);
    }
}

// Sélectionner un avatar
function selectAvatar(avatarPath) {
    console.log('🎯 Avatar sélectionné:', avatarPath);
    
    // Mettre à jour la variable globale
    selectedAvatar = avatarPath;
    
    // Mettre à jour l'avatar actuel
    document.getElementById('currentAvatar').src = avatarPath;
    
    // Mettre à jour la sélection dans la grille
    updateAvatarSelection();
}

// Mettre à jour la sélection d'avatar dans la grille
function updateAvatarSelection() {
    const avatarOptions = document.querySelectorAll('.avatar-option');
    
    avatarOptions.forEach(option => {
        option.classList.remove('selected');
        if (option.dataset.avatar === selectedAvatar) {
            option.classList.add('selected');
        }
    });
}

// Valider les mots de passe
function validatePassword() {
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    const confirmField = document.getElementById('confirmPassword');
    
    if (newPassword && confirmPassword) {
        if (newPassword === confirmPassword) {
            confirmField.setCustomValidity('');
            confirmField.classList.remove('is-invalid');
            confirmField.classList.add('is-valid');
        } else {
            confirmField.setCustomValidity('Les mots de passe ne correspondent pas');
            confirmField.classList.remove('is-valid');
            confirmField.classList.add('is-invalid');
        }
    } else if (newPassword && !confirmPassword) {
        confirmField.setCustomValidity('Veuillez confirmer le nouveau mot de passe');
        confirmField.classList.remove('is-valid');
        confirmField.classList.add('is-invalid');
    } else {
        confirmField.setCustomValidity('');
        confirmField.classList.remove('is-invalid', 'is-valid');
    }
}

// Gérer la mise à jour du profil
async function handleProfileUpdate(event) {
    event.preventDefault();
    
    console.log('💾 Mise à jour du profil...');
    
    try {
        // Validation des champs
        const currentPassword = document.getElementById('currentPassword').value;
        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;
        
        if (!currentPassword) {
            showToast('❌ Mot de passe actuel requis', 'Erreur de validation', 'error');
            return;
        }
        
        if (newPassword && newPassword !== confirmPassword) {
            showToast('❌ Les mots de passe ne correspondent pas', 'Erreur de validation', 'error');
            return;
        }
        
        if (newPassword && newPassword.length < 6) {
            showToast('❌ Le nouveau mot de passe doit contenir au moins 6 caractères', 'Erreur de validation', 'error');
            return;
        }
        
        // Préparer les données
        const updateData = {
            currentUsername: currentUser.username,
            currentPassword: currentPassword,
            newUsername: currentUser.username, // On ne peut pas modifier le username
            newPassword: newPassword || null,
            newProfileUrl: selectedAvatar
        };
        
        console.log('📤 Données de mise à jour:', updateData);
        
        // Appel API pour mettre à jour le profil
        const response = await fetch('/api/profile', {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(updateData)
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || `Erreur HTTP: ${response.status}`);
        }
        
        const result = await response.json();
        
        console.log('✅ Profil mis à jour:', result);
        
        // Mettre à jour les données locales
        currentUser = result.user;
        
        // Afficher le succès
        showToast('✅ Profil mis à jour avec succès', 'Succès', 'success');
        
        // Réinitialiser le formulaire
        resetForm();
        
    } catch (error) {
        console.error('❌ Erreur lors de la mise à jour du profil:', error);
        showToast('❌ Erreur lors de la mise à jour', error.message, 'error');
    }
}

// Réinitialiser le formulaire
function resetForm() {
    document.getElementById('profileForm').reset();
    document.getElementById('confirmPassword').classList.remove('is-invalid', 'is-valid');
    
    // Remettre l'avatar actuel
    if (currentUser && currentUser.profileUrl) {
        selectedAvatar = currentUser.profileUrl;
        document.getElementById('currentAvatar').src = currentUser.profileUrl;
    } else {
        selectedAvatar = '/profil/avatar1.svg';
        document.getElementById('currentAvatar').src = '/profil/avatar1.svg';
    }
    
    updateAvatarSelection();
}

// Afficher un toast
function showToast(message, title = 'Notification', type = 'info') {
    const toastElement = document.getElementById('toast');
    const toastTitle = document.getElementById('toastTitle');
    const toastBody = document.getElementById('toastBody');
    
    // Définir les couleurs selon le type
    const colors = {
        success: 'text-success',
        error: 'text-danger',
        warning: 'text-warning',
        info: 'text-info'
    };
    
    toastTitle.textContent = title;
    toastTitle.className = `me-auto ${colors[type] || colors.info}`;
    toastBody.textContent = message;
    
    toast.show();
}

// Aller au lobby
function goToLobby() {
    window.location.href = '/lobby.html';
}

// Basculer le thème
function toggleTheme() {
    const html = document.documentElement;
    const themeToggle = document.getElementById('themeToggle');
    const icon = themeToggle.querySelector('i');
    
    if (html.getAttribute('data-bs-theme') === 'dark') {
        html.setAttribute('data-bs-theme', 'light');
        icon.className = 'fas fa-sun';
    } else {
        html.setAttribute('data-bs-theme', 'dark');
        icon.className = 'fas fa-moon';
    }
}
