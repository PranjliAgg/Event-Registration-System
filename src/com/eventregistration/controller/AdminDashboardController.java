package com.eventregistration.controller;

import com.eventregistration.dao.EventDAO;
import com.eventregistration.dao.RegistrationDAO;
import com.eventregistration.dao.UserDAO;
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

public class AdminDashboardController implements Initializable {

    @FXML private FlowPane adminEventsFlow;
    @FXML private VBox adminRegistrationsContainer;
    @FXML private VBox adminRevenueContainer;
    @FXML private Label totalUsersLbl;
    @FXML private Label statusLabel;

    @FXML private TextField eventNameField;
    @FXML private TextField descField;
    @FXML private DatePicker eventDatePicker;
    @FXML private DatePicker deadlinePicker;
    @FXML private TextField totalSeatsField;
    @FXML private TextField priceField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> venueCombo;
    @FXML private Label formTitleLabel;
    @FXML private Button actionButton;

    private User currentUser;
    private final EventDAO eventDAO = new EventDAO();
    private final RegistrationDAO regDAO = new RegistrationDAO();
    private final UserDAO userDAO = new UserDAO();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        refreshUI();
    }

    public void setUser(User user) {
        this.currentUser = user;
        refreshUI();
    }

    private void refreshUI() {
        loadEvents();
        loadAllRegistrations();
        loadRevenueReport();
        loadDashboardStats();
    }

    private void updateEventStatusesAutomatically() {
        List<Event> allEvents = eventDAO.getAllUpcomingEvents();
        java.time.LocalDate today = java.time.LocalDate.now();

        for (Event event : allEvents) {
            String currentStatus = event.getStatus();
            String newStatus = null;

            if ("upcoming".equals(currentStatus)) {
                if (event.getEventDate().isBefore(today)) {
                    // Event date is in the past
                    newStatus = "completed";
                } else if (event.getEventDate().equals(today)) {
                    // Event is today
                    newStatus = "ongoing";
                }
            } else if ("ongoing".equals(currentStatus) && event.getEventDate().isBefore(today)) {
                // Ongoing event that has passed should be completed
                newStatus = "completed";
            }

            if (newStatus != null && !newStatus.equals(currentStatus)) {
                eventDAO.updateEventStatus(event.getEventId(), newStatus);
                System.out.println("Auto-updated event '" + event.getEventName() + "' status from '" + currentStatus + "' to '" + newStatus + "'");
            }
        }
    }

    private void loadEvents() {
        if (adminEventsFlow == null) {
            return;  // Events pane not available in current scene
        }

        // Auto-update event statuses based on dates
        updateEventStatusesAutomatically();

        List<Event> events = eventDAO.getAllUpcomingEvents();
        adminEventsFlow.getChildren().clear();

        for (Event event : events) {
            VBox card = new VBox(10);
            card.setPadding(new Insets(16));
            card.setAlignment(Pos.TOP_LEFT);
            card.getStyleClass().add("event-card");
            card.setPrefWidth(350);
            card.setMinWidth(320);

            Label name = new Label(event.getEventName());
            name.getStyleClass().add("card-title");
            name.setWrapText(true);

            // Create organized info layout
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
            Label venueValue = new Label(event.getVenueName() + " (" + event.getVenueLocation() + ")");
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
                descContent.setMaxWidth(300);
                descriptionBox.getChildren().addAll(descTitle, descContent);
            }

            Label status = new Label("Status: " + event.getStatus());
            status.getStyleClass().add("status-label");

            HBox buttonContainer = new HBox(10);
            buttonContainer.setAlignment(Pos.CENTER_LEFT);
            buttonContainer.setPadding(new Insets(8, 0, 0, 0));

            Button cancelBtn = new Button("Cancel Event");
            cancelBtn.getStyleClass().add("btn-danger");

            Button editBtn = new Button("Edit Event");
            editBtn.getStyleClass().add("btn-primary-modern");

            cancelBtn.setOnAction(ev -> {
                if (eventDAO.updateEventStatus(event.getEventId(), "cancelled")) {
                    showStatus("Event cancelled.", true);
                    refreshUI();
                } else {
                    showStatus("Failed to cancel event.", false);
                }
            });

            editBtn.setOnAction(ev -> {
                try {
                    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/AdminCreateEvent.fxml"));
                    javafx.scene.Parent root = loader.load();
                    AdminDashboardController controller = loader.getController();
                    controller.setUser(currentUser);
                    controller.populateEditForm(event); // Pass the event to edit
                    Stage stage = (Stage) adminEventsFlow.getScene().getWindow();
                    SceneManager.loadScene(stage, root, "Edit Event", 1100, 700);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showStatus("Navigation error: " + ex.getMessage(), false);
                }
            });

            buttonContainer.getChildren().addAll(cancelBtn, editBtn);

            // Build the card content
            if (descriptionBox != null) {
                card.getChildren().addAll(name, infoGrid, descriptionBox, status, buttonContainer);
            } else {
                card.getChildren().addAll(name, infoGrid, status, buttonContainer);
            }

            adminEventsFlow.getChildren().add(card);
        }
    }

    private void loadAllRegistrations() {
        if (adminRegistrationsContainer == null) {
            return;  // Registrations displayed in separate scene
        }
        List<Object[]> regList = regDAO.getAllRegistrations();
        adminRegistrationsContainer.getChildren().clear();

        if (regList.isEmpty()) {
            Label empty = new Label("No registrations available.");
            empty.getStyleClass().add("info-label");
            adminRegistrationsContainer.getChildren().add(empty);
            return;
        }

        for (Object[] row : regList) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dde3f0; -fx-border-radius: 10; -fx-background-radius: 10;");

            Label user = new Label("User: " + row[1] + " (" + row[2] + ")");
            user.getStyleClass().add("card-title");
            Label event = new Label("Event: " + row[3] + " | Date: " + row[4]);
            Label status = new Label("Reg: " + row[5] + " | Payment: " + row[7]);
            Label amount = new Label("Amount: ₹" + row[6]);

            card.getChildren().addAll(user, event, status, amount);
            adminRegistrationsContainer.getChildren().add(card);
        }
    }

    private void loadRevenueReport() {
        if (adminRevenueContainer == null) return;
        
        adminRevenueContainer.getChildren().clear();
        List<Object[]> revenuData = eventDAO.getRevenueReport();
        
        if (revenuData.isEmpty()) {
            Label empty = new Label("No revenue data available.");
            empty.getStyleClass().add("info-label");
            adminRevenueContainer.getChildren().add(empty);
            return;
        }

        for (Object[] row : revenuData) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dde3f0; -fx-border-radius: 10; -fx-background-radius: 10;");

            Label event = new Label("Event: " + row[0]);
            event.getStyleClass().add("card-title");
            Label category = new Label("Category: " + row[1]);
            Label fill = new Label("Fill: " + row[2] + " (" + row[3] + ")");
            Label revenue = new Label("Revenue: " + row[4]);
            revenue.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #27ae60;");

            card.getChildren().addAll(event, category, fill, revenue);
            adminRevenueContainer.getChildren().add(card);
        }
    }

    private void loadDashboardStats() {
        if (totalUsersLbl != null) {
            totalUsersLbl.setText(String.valueOf(userDAO.getTotalUsers()));
        }
    }

    private void loadCategoryCombo() {
        if (categoryCombo == null) return;
        categoryCombo.setItems(javafx.collections.FXCollections.observableArrayList(eventDAO.getAllCategories()));
    }

    private void loadVenueCombo() {
        if (venueCombo == null) return;
        venueCombo.setItems(javafx.collections.FXCollections.observableArrayList(eventDAO.getAllVenues()));
    }

    private Event editingEvent = null; // Track if we're editing an event

    private void populateEditForm(Event event) {
        editingEvent = event;
        eventNameField.setText(event.getEventName());
        descField.setText(event.getDescription());
        eventDatePicker.setValue(event.getEventDate());
        deadlinePicker.setValue(event.getRegistrationDeadline());
        totalSeatsField.setText(String.valueOf(event.getTotalSeats()));
        priceField.setText(String.format("%.2f", event.getPrice()));
        categoryCombo.setValue(event.getCategoryName());
        venueCombo.setValue(event.getVenueName());

        // Update UI for editing mode
        if (formTitleLabel != null) {
            formTitleLabel.setText("Edit Event");
        }
        if (actionButton != null) {
            actionButton.setText("Update Event");
        }

        showStatus("Editing event: " + event.getEventName(), true);
    }

    @FXML
    private void handleAddEvent(ActionEvent e) {
        try {
            if (eventNameField.getText().trim().isEmpty()) { showStatus("Event name required.", false); return; }
            if (eventDatePicker.getValue() == null) { showStatus("Event date required.", false); return; }
            if (deadlinePicker.getValue() == null) { showStatus("Deadline required.", false); return; }
            if (eventDatePicker.getValue().isBefore(java.time.LocalDate.now())) { showStatus("Event date can not be past.", false); return; }
            if (!deadlinePicker.getValue().isBefore(eventDatePicker.getValue())) { showStatus("Deadline must be before event.", false); return; }

            int totalSeats = Integer.parseInt(totalSeatsField.getText().trim());
            double price = priceField.getText().trim().isEmpty() ? 0 : Double.parseDouble(priceField.getText().trim());
            if (totalSeats <= 0) { showStatus("Total seats must be positive.", false); return; }

            Event ev = new Event();
            ev.setEventName(eventNameField.getText().trim());
            ev.setDescription(descField.getText());
            ev.setEventDate(eventDatePicker.getValue());
            ev.setRegistrationDeadline(deadlinePicker.getValue());
            ev.setTotalSeats(totalSeats); ev.setPrice(price);
            ev.setCategoryId(eventDAO.getCategoryIdByName(categoryCombo.getValue()));
            ev.setVenueId(eventDAO.getVenueIdByName(venueCombo.getValue()));

            boolean success;
            String action;

            if (editingEvent != null) {
                // Update existing event
                ev.setEventId(editingEvent.getEventId());
                success = eventDAO.updateEvent(ev);
                action = "updated";
            } else {
                // Create new event
                success = eventDAO.addEvent(ev);
                action = "created";
            }

            if (success) {
                showStatus("Event " + action + " successfully.", true);
                clearEventForm();
                editingEvent = null; // Reset editing state
                refreshUI();
            } else {
                showStatus("Failed to " + action + " event.", false);
            }
        } catch (Exception ex) {
            showStatus("Error " + (editingEvent != null ? "updating" : "creating") + " event: " + ex.getMessage(), false);
        }
    }

    private void clearEventForm() {
        eventNameField.clear(); descField.clear(); totalSeatsField.clear(); priceField.clear();
        eventDatePicker.setValue(null); deadlinePicker.setValue(null);
        categoryCombo.setValue(null); venueCombo.setValue(null);
        editingEvent = null; // Reset editing state

        // Reset UI to create mode
        if (formTitleLabel != null) {
            formTitleLabel.setText("Create New Event");
        }
        if (actionButton != null) {
            actionButton.setText("Create Event");
        }
    }

    @FXML
    private void handleRefresh(ActionEvent e) { refreshUI(); }

    @FXML
    private void handleNavCreateEvent(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/AdminCreateEvent.fxml"));
            javafx.scene.Parent root = loader.load();
            AdminDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "Create Event", 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
            showStatus("Navigation error: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleNavRegistrations(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/AdminRegistrations.fxml"));
            javafx.scene.Parent root = loader.load();
            AdminDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            controller.loadAllRegistrations();
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "All Registrations", 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
            showStatus("Navigation error: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleNavRevenueReport(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/AdminRevenueReport.fxml"));
            javafx.scene.Parent root = loader.load();
            AdminDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            controller.loadRevenueReport();
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "Revenue Report", 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
            showStatus("Navigation error: " + ex.getMessage(), false);
        }
    }

    @FXML
    private void handleBackToMain(ActionEvent e) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/AdminDashboard.fxml"));
            javafx.scene.Parent root = loader.load();
            AdminDashboardController controller = loader.getController();
            controller.setUser(currentUser);
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            SceneManager.loadScene(stage, root, "Admin Dashboard", 1100, 700);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void handleClearForm(ActionEvent e) {
        clearEventForm();
        showStatus("Form cleared.", true);
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
