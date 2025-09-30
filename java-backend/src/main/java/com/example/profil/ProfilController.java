package com.example.profil;


import com.example.http.HttpController;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfilController implements HttpController {

    private static final Logger logger = LoggerFactory.getLogger(ProfilController.class);
    private final ProfilService profilService;

    public ProfilController(Vertx vertx) {
        this.profilService = new ProfilService();
    }

    @Override
    public void registerRoutes(Router router) {
        // Route pour récupérer les infos utilisateur
        router.get("/api/me").handler(this::handleGetCurrentUser);
        
        // Route pour modifier le profil utilisateur
        router.put("/api/profile").handler(this::handleUpdateProfile);
        
        // Route pour récupérer les photos de profil prédéfinies
        router.get("/api/profile/pictures").handler(this::handleGetProfilePictures);
    }

    private void handleGetCurrentUser(RoutingContext ctx) {
        // Récupérer le username depuis les paramètres de requête
        String username = ctx.request().getParam("username");
        if (username == null || username.trim().isEmpty()) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Username parameter is required").encode());
            return;
        }
        
        // Récupérer le profileUrl depuis la base de données
        JsonObject queryData = new JsonObject()
            .put("username", username);
        
        profilService.getUserProfile(queryData, res -> {
            if (res.succeeded()) {
                JsonObject userProfile = res.result();
                JsonObject response = new JsonObject()
                    .put("username", userProfile.getString("username"))
                    .put("userId", userProfile.getInteger("id"))
                    .put("profileUrl", userProfile.getString("profileUrl"));
                
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(response.encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(404)
                    .end(new JsonObject().put("error", "User not found").encode());
            }
        });
    }

    private void handleUpdateProfile(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        if (body == null) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Invalid JSON").encode());
            return;
        }

        // Récupérer le username depuis le body de la requête
        String currentUsername = body.getString("currentUsername");
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .setStatusCode(400)
                .end(new JsonObject().put("error", "currentUsername is required").encode());
            return;
        }

        profilService.updateProfile(body, res -> {
            if (res.succeeded()) {
                JsonObject updatedUser = res.result();
                
                JsonObject response = new JsonObject()
                    .put("message", updatedUser.getString("message"))
                    .put("user", new JsonObject()
                        .put("username", updatedUser.getString("username"))
                        .put("userId", updatedUser.getInteger("id"))
                        .put("profileUrl", updatedUser.getString("profileUrl")));
                
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(200)
                    .end(response.encode());
            } else {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "Update failed: " + res.cause().getMessage()).encode());
            }
        });
    }

    private void handleGetProfilePictures(RoutingContext ctx) {
        // Retourner les 10 photos de profil prédéfinies
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("pictures", new JsonObject()
                .put("1", "/images/profiles/profile1.svg")
                .put("2", "/images/profiles/profile2.svg")
                .put("3", "/images/profiles/profile3.svg")
                .put("4", "/images/profiles/profile4.svg")
                .put("5", "/images/profiles/profile5.svg")
                .put("6", "/images/profiles/profile6.svg")
                .put("7", "/images/profiles/profile7.svg")
                .put("8", "/images/profiles/profile8.svg")
                .put("9", "/images/profiles/profile9.svg")
                .put("10", "/images/profiles/profile10.svg")
            );
        
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(200)
            .end(response.encode());
    }
}



