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
    @FXML private ComboBox<String> statusCombo;

    private User currentUser;
    private final EventDAO eventDAO = new EventDAO();
    private final RegistrationDAO regDAO = new RegistrationDAO();
    private final UserDAO userDAO = new UserDAO();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (statusCombo != null) {
            statusCombo.setItems(javafx.collections.FXCollections.observableArrayList("upcoming", "ongoing", "completed", "cancelled"));
            statusCombo.setValue("upcoming");
        }
        refreshUI();
    }

    public void setUser(User user) {
        this.currentUser = user;
        refreshUI();
    }

    private void refreshUI() {
        loadEvents();
        loadDashboardStats();
        loadCategoryCombo();
        loadVenueCombo();
    }

    private void loadEvents() {
        if (adminEventsFlow == null) {
            return;  // Events pane not available in current scene
        }
        List<Event> events = eventDAO.getAllUpcomingEvents();
        adminEventsFlow.getChildren().clear();

        for (Event event : events) {
            VBox card = new VBox(8);
            card.setPadding(new Insets(12));
            card.setAlignment(Pos.TOP_LEFT);
            card.getStyleClass().add("event-card");

            Label name = new Label(event.getEventName());
            name.getStyleClass().add("card-title");
            Label info = new Label("Date: " + event.getEventDate() + " | Category: " + event.getCategoryName());
            info.getStyleClass().add("card-meta");
            Label status = new Label("Status: " + event.getStatus());
            status.getStyleClass().add("status-label");

            String selectedStatus = "upcoming";
            boolean canUpdateStatus = false;
            if (statusCombo != null && statusCombo.getValue() != null && !statusCombo.getValue().isEmpty()) {
                selectedStatus = statusCombo.getValue();
                canUpdateStatus = true;
            }

            Button action = new Button("Set status: " + selectedStatus);
            action.getStyleClass().add("btn-secondary");
            action.setDisable(!canUpdateStatus);

            action.setOnAction(ev -> {
                if (statusCombo == null || statusCombo.getValue() == null || statusCombo.getValue().isEmpty()) {
                    showStatus("Please select a valid status first.", false);
                    return;
                }

                if (eventDAO.updateEventStatus(event.getEventId(), statusCombo.getValue())) {
                    showStatus("Status updated.", true);
                    refreshUI();
                } else {
                    showStatus("Failed to update status.", false);
                }
            });

            if (canUpdateStatus) {
                card.getChildren().addAll(name, info, status, action);
            } else {
                card.getChildren().addAll(name, info, status);
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

            if (eventDAO.addEvent(ev)) {
                showStatus("Event created successfully.", true);
                clearEventForm();
                refreshUI();
            } else {
                showStatus("Failed to create event.", false);
            }
        } catch (Exception ex) {
            showStatus("Error creating event: " + ex.getMessage(), false);
        }
    }

    private void clearEventForm() {
        eventNameField.clear(); descField.clear(); totalSeatsField.clear(); priceField.clear();
        eventDatePicker.setValue(null); deadlinePicker.setValue(null);
        categoryCombo.setValue(null); venueCombo.setValue(null);
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
