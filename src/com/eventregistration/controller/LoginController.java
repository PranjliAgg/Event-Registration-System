package com.eventregistration.controller;

import com.eventregistration.dao.UserDAO;
import com.eventregistration.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginBtn;
    @FXML private Hyperlink     registerLink;

    private final UserDAO userDAO = new UserDAO();

    @FXML
    private void handleLogin(ActionEvent e) {
        String email    = emailField.getText().trim();
        String password = passwordField.getText().trim();

        System.out.println("[LOGIN] Attempting login with email: " + email);

        if (email.isEmpty() || password.isEmpty()) {
            System.out.println("[LOGIN] Error: Empty fields");
            showError("Please fill in all fields.");
            return;
        }

        User user = userDAO.login(email, password);
        if (user == null) {
            System.out.println("[LOGIN] Error: No user found or invalid credentials");
            showError("Invalid email or password.");
            return;
        }

        try {
            String role = user.getRole() == null ? "user" : user.getRole().trim().toLowerCase();
            String fxmlFile = role.equals("admin") ? "/fxml/AdminDashboard.fxml" : "/fxml/UserDashboard.fxml";
            System.out.println("[LOGIN] Loading FXML: " + fxmlFile + " for role=" + role);

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();
            if (role.equals("admin")) {
                ((AdminDashboardController) loader.getController()).setUser(user);
            } else {
                ((UserDashboardController) loader.getController()).setUser(user);
            }

            Stage stage = (Stage) loginBtn.getScene().getWindow();
            SceneManager.loadScene(stage, root, "Event Registration - " + user.getName(), 1100, 700);
            System.out.println("[LOGIN] Navigation successful");
        } catch (Exception ex) {
            System.err.println("[LOGIN] Error: " + ex.getMessage());
            ex.printStackTrace();
            showError("Navigation error: " + ex.getMessage());
        }
    }

    @FXML
    private void handleRegisterLink(ActionEvent e) {
        try {
            Stage stage = (Stage) registerLink.getScene().getWindow();
            SceneManager.loadScene(stage, "/fxml/Register.fxml", "Register - Event Registration", 900, 600);
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Navigation error: " + ex.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    @FXML
    protected void handleLogout() {
        try {
            emailField.clear();
            passwordField.clear();
            errorLabel.setVisible(false);
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            SceneManager.loadScene(stage, "/fxml/Login.fxml", "Event Registration", 900, 600);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
