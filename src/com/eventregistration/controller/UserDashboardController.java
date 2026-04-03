package com.eventregistration.controller;

import com.eventregistration.dao.EventDAO;
import com.eventregistration.dao.RegistrationDAO;
import com.eventregistration.model.Event;
import com.eventregistration.model.User;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class UserDashboardController implements Initializable {

    @FXML private FlowPane eventsFlowPane;
    @FXML private VBox registrationsContainer;
    @FXML private VBox userRegistrationsContainer;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterCategoryCombo;
    @FXML private ComboBox<String> filterVenueCombo;
    @FXML private ComboBox<String> payModeCombo;
    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;

    private User currentUser;
    private Event selectedEvent;

    private final EventDAO eventDAO = new EventDAO();
    private final RegistrationDAO regDAO = new RegistrationDAO();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (payModeCombo != null) {
            payModeCombo.setItems(javafx.collections.FXCollections.observableArrayList(
                    "free", "cash", "card", "upi", "netbanking"));
            payModeCombo.setValue("upi");
        }

        if (filterCategoryCombo != null) {
            filterCategoryCombo.setItems(javafx.collections.FXCollections.observableArrayList(eventDAO.getAllCategories()));
        }
        if (filterVenueCombo != null) {
            filterVenueCombo.setItems(javafx.collections.FXCollections.observableArrayList(eventDAO.getAllVenues()));
        }

        loadEvents();
    }

    public void setUser(User user) {
        this.currentUser = user;
        welcomeLabel.setText("Welcome, " + user.getName() + "!");
        loadEvents();
    }

    private VBox buildEventCard(Event event) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(16));
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("event-card");
        card.setPrefWidth(300);
        card.setMinWidth(280);

        Label labelName = new Label(event.getEventName());
        labelName.getStyleClass().add("card-title");
        labelName.setWrapText(true);

        // Create a more organized layout with GridPane for better spacing
        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(10);
        infoGrid.setVgap(6);

        Label dateLabel = new Label("📅 Date:");
        dateLabel.getStyleClass().add("info-label");
        Label dateValue = new Label(event.getEventDate().toString());
        dateValue.getStyleClass().add("card-meta");

        Label categoryLabel = new Label("🏷️ Category:");
        categoryLabel.getStyleClass().add("info-label");
        Label categoryValue = new Label(event.getCategoryName());
        categoryValue.getStyleClass().add("card-meta");

        Label venueLabel = new Label("📍 Venue:");
        venueLabel.getStyleClass().add("info-label");
        Label venueValue = new Label(event.getVenueName());
        venueValue.getStyleClass().add("card-meta");
        venueValue.setWrapText(true);

        Label seatsLabel = new Label("👥 Seats:");
        seatsLabel.getStyleClass().add("info-label");
        Label seatsValue = new Label(event.getAvailableSeats() + "/" + event.getTotalSeats());
        seatsValue.getStyleClass().add("card-meta");

        Label priceLabel = new Label("💰 Price:");
        priceLabel.getStyleClass().add("info-label");
        Label priceValue = new Label("₹" + String.format("%.2f", event.getPrice()));
        priceValue.getStyleClass().add("card-meta");

        // Add labels to grid
        infoGrid.add(dateLabel, 0, 0);
        infoGrid.add(dateValue, 1, 0);
        infoGrid.add(categoryLabel, 0, 1);
        infoGrid.add(categoryValue, 1, 1);
        infoGrid.add(venueLabel, 0, 2);
        infoGrid.add(venueValue, 1, 2);
        infoGrid.add(seatsLabel, 0, 3);
        infoGrid.add(seatsValue, 1, 3);
        infoGrid.add(priceLabel, 0, 4);
        infoGrid.add(priceValue, 1, 4);

        // Add description if available
        VBox descriptionBox = null;
        if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
            descriptionBox = new VBox(4);
            Label descTitle = new Label("📝 Description:");
            descTitle.getStyleClass().add("info-label");
            Label descContent = new Label(event.getDescription());
            descContent.getStyleClass().add("card-meta");
            descContent.setWrapText(true);
            descContent.setMaxWidth(250);
            descriptionBox.getChildren().addAll(descTitle, descContent);
        }

        Label labelStatus = new Label("Status: " + event.getStatus());
        labelStatus.getStyleClass().add("status-label");

        // Build the card content
        if (descriptionBox != null) {
            card.getChildren().addAll(labelName, infoGrid, descriptionBox, labelStatus);
        } else {
            card.getChildren().addAll(labelName, infoGrid, labelStatus);
        }

        card.setOnMouseClicked(e -> {
            selectedEvent = event;
            for (javafx.scene.Node n : eventsFlowPane.getChildren()) {
                n.getStyleClass().remove("event-card-selected");
            }
            card.getStyleClass().add("event-card-selected");
            showStatus("Selected event: " + event.getEventName(), true);
        });

        return card;
    }

    private void loadEvents() {
        if (eventsFlowPane == null) {
            return;  // Events pane not available in current scene
        }
        List<Event> events = eventDAO.searchEvents(
                searchField == null ? "" : searchField.getText().trim(),
                filterCategoryCombo == null ? null : filterCategoryCombo.getValue(),
                filterVenueCombo == null ? null : filterVenueCombo.getValue());

        eventsFlowPane.getChildren().clear();
        if (events.isEmpty()) {
            Label empty = new Label("No events available.");
            empty.getStyleClass().add("info-label");
            eventsFlowPane.getChildren().add(empty);
            return;
        }

        for (Event event : events) {
            eventsFlowPane.getChildren().add(buildEventCard(event));
        }
    }

    private void loadMyRegistrations() {
        if (registrationsContainer == null) {
            return;  // Registrations are now displayed in a separate scene
        }
        registrationsContainer.getChildren().clear();

        List<Object[]> rows = regDAO.getUserRegistrations(currentUser == null ? -1 : currentUser.getUserId());
        if (rows.isEmpty()) {
            Label empty = new Label("You have not registered for any events yet.");
            empty.getStyleClass().add("info-label");
            registrationsContainer.getChildren().add(empty);
            return;
        }

        for (Object[] row : rows) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dde3f0; -fx-border-radius: 10; -fx-background-radius: 10;");

            Label eventName = new Label("Event: " + row[1]);
            eventName.getStyleClass().add("card-title");
            Label eventDate = new Label("Date: " + row[2]);
            Label status = new Label("Status: " + row[4]);
            Label amount = new Label("Amount: ₹" + row[5] + " | Payment: " + row[7]);

            Button cancelBtn = new Button("Cancel");
            cancelBtn.getStyleClass().add("btn-danger");
            cancelBtn.setOnAction(ev -> {
                int regId = (int) row[0];
                String result = regDAO.cancelRegistration(regId);
                showStatus(result, result.startsWith("SUCCESS"));
                loadEvents();
                loadMyRegistrations();
            });

            card.getChildren().addAll(eventName, eventDate, status, amount, cancelBtn);
            registrationsContainer.getChildren().add(card);
        }
    }

    public void loadUserRegistrations() {
        if (userRegistrationsContainer == null || currentUser == null) {
            return;
        }
        List<Object[]> rows = regDAO.getUserRegistrations(currentUser.getUserId());
        userRegistrationsContainer.getChildren().clear();

        if (rows.isEmpty()) {
            Label empty = new Label("You have not registered for any events yet.");
            empty.getStyleClass().add("info-label");
            userRegistrationsContainer.getChildren().add(empty);
            return;
        }

        for (Object[] row : rows) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dde3f0; -fx-border-radius: 10; -fx-background-radius: 10;");

            Label eventName = new Label("Event: " + row[1]);
            eventName.getStyleClass().add("card-title");
            Label eventDate = new Label("Date: " + row[2]);
            Label regStatus = new Label("Status: " + row[4]);
            Label amount = new Label("Amount: ₹" + row[5] + " | Payment: " + row[7]);

            HBox btnContainer = new HBox(10);
            btnContainer.setAlignment(Pos.CENTER_LEFT);

            Button cancelBtn = new Button("Cancel Registration");
            cancelBtn.getStyleClass().add("btn-danger");
            
            int regId = (int) row[0];
            String regStatusStr = row[4] != null ? row[4].toString() : "";
            
            if ("cancelled".equalsIgnoreCase(regStatusStr)) {
                cancelBtn.setDisable(true);
                cancelBtn.setText("Cancelled");
            }
            
            cancelBtn.setOnAction(ev -> {
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Cancellation");
                confirmDialog.setHeaderText("Cancel Registration?");
                confirmDialog.setContentText("Are you sure you want to cancel registration for " + row[1] + "?\n\nAmount ₹" + row[5] + " will be refunded if payment was completed.");
                
                java.util.Optional<ButtonType> result = confirmDialog.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    String cancelResult = regDAO.cancelRegistration(regId);
                    showStatus(cancelResult, cancelResult.startsWith("SUCCESS"));
                    
                    if (cancelResult.startsWith("SUCCESS")) {
                        cancelBtn.setDisable(true);
                        cancelBtn.setText("Cancelled");
                        regStatus.setText("Status: cancelled");
                    }
                }
            });

            btnContainer.getChildren().add(cancelBtn);
            card.getChildren().addAll(eventName, eventDate, regStatus, amount, btnContainer);
            userRegistrationsContainer.getChildren().add(card);
        }
    }

    @FXML
    private void handleSearch(ActionEvent e) {
        loadEvents();
    }

    @FXML
    private void clearFilters(ActionEvent e) {
        if (searchField != null) searchField.clear();
        if (filterCategoryCombo != null) filterCategoryCombo.setValue(null);
        if (filterVenueCombo != null) filterVenueCombo.setValue(null);
        loadEvents();
    }

    @FXML
    private void handleRegister(ActionEvent e) {
        if (selectedEvent == null) {
            showStatus("Select an event card before registering.", false);
            return;
        }

        if (regDAO.isAlreadyRegistered(currentUser.getUserId(), selectedEvent.getEventId())) {
            showStatus("Already registered for this event.", false);
            return;
        }

        double amount = selectedEvent.getPrice();
        String mode = payModeCombo == null ? "free" : payModeCombo.getValue();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Registration");
        confirm.setHeaderText("Confirm Your Registration");
        confirm.setContentText("Event: " + selectedEvent.getEventName() + "\nPrice: ₹" + String.format("%.2f", amount) + "\nMode: " + mode);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String regResult = regDAO.registerUser(currentUser.getUserId(), selectedEvent.getEventId(), amount, mode);
        boolean success = regResult.startsWith("SUCCESS") || regResult.startsWith("WAITLISTED");
        if (success) {
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Registration Status");
            info.setHeaderText("Registration Processed");
            info.setContentText(regResult);
            info.showAndWait();
        }
        showStatus(regResult, success);
        loadEvents();
        loadMyRegistrations();
    }

    @FXML
    private void handleRefresh(ActionEvent e) {
        loadEvents();
    }

    @FXML
    private void handleNavMyRegistrations(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/UserRegistrations.fxml"));
            javafx.scene.Parent root = loader.load();
            UserDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            controller.loadUserRegistrations();
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "My Registrations - " + currentUser.getName(), 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
            showStatus("Navigation error: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleBackToMain(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/UserDashboard.fxml"));
            javafx.scene.Parent root = loader.load();
            UserDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "Dashboard - " + currentUser.getName(), 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void handleLogout(ActionEvent e) {
        try {
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, "/fxml/Login.fxml", "Event Registration", 900, 600);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void showStatus(String message, boolean success) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        statusLabel.getStyleClass().add(success ? "success-label" : "error-label");
        statusLabel.setVisible(true);
    }
}
