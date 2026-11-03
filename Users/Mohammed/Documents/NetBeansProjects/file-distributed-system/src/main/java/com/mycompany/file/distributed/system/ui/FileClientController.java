package com.mycompany.file.distributed.system.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.File;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// control the screen
public class FileClientController implements Initializable {

    // things on screen
    @FXML private TextField  hostField;
    @FXML private TextField  portField;
    @FXML private Button     connectBtn;
    @FXML private Label      statusDot;
    @FXML private Label      statusLabel;
    @FXML private Button     listBtn;
    @FXML private Button     uploadBtn;
    @FXML private Button     downloadBtn;
    @FXML private Button     deleteBtn;

    // things on screen
    @FXML private TableView<FileEntry>          fileTable;
    @FXML private TableColumn<FileEntry, String> nameColumn;
    @FXML private TableColumn<FileEntry, String> sizeColumn;

    // things on screen
    @FXML private ProgressBar progressBar;
    @FXML private Label       progressLabel;
    @FXML private TextArea    logArea;

    // things on screen
    private final FileClientService          service  = new FileClientService();
    private final ExecutorService            executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tcp-worker");
        t.setDaemon(true);
        return t;
    });
    private final ObservableList<FileEntry>  files    = FXCollections.observableArrayList();
    private static final DateTimeFormatter   TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // start UI
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // setup table
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        sizeColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getFormattedSize()));
        sizeColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        fileTable.setItems(files);
        fileTable.setPlaceholder(new Label("Connect to a server and click List Files"));

        // double click get file
        fileTable.setRowFactory(tv -> {
            TableRow<FileEntry> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    handleDownload();
                }
            });
            return row;
        });

        // right click menu
        ContextMenu ctx = new ContextMenu();
        MenuItem dlItem  = new MenuItem("⬇  Download");
        MenuItem delItem = new MenuItem("🗑  Delete");
        dlItem.setOnAction(e  -> handleDownload());
        delItem.setOnAction(e -> handleDelete());
        ctx.getItems().addAll(dlItem, delItem);
        fileTable.setContextMenu(ctx);

        setActionsEnabled(false);
        progressBar.setProgress(0);
        log("Ready — enter host and port, then click Connect.");
    }

    // make connection
    @FXML
    private void handleConnect() {

        if (service.isConnected()) {
            service.disconnect();
            setConnected(false);
            log("Disconnected from server.");
            return;
        }

        String host = hostField.getText().trim().isEmpty()
                      ? "localhost" : hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            log("ERROR: Invalid port number — enter a number (e.g. 9090).");
            return;
        }

        connectBtn.setDisable(true);
        log("Connecting to " + host + ":" + port + " …");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return service.connect(host, port);
            }
        };

        task.setOnSucceeded(e -> {
            setConnected(true);
            log("Connected to " + host + ":" + port);
            log("[Server] " + task.getValue());
            connectBtn.setDisable(false);
            handleList(); // auto-refresh on connect
        });

        task.setOnFailed(e -> {
            log("ERROR: " + task.getException().getMessage());
            connectBtn.setDisable(false);
        });

        executor.execute(task);
    }

    // get list
    @FXML
    private void handleList() {
        if (!checkConnected()) return;

        setBusy(true, "Fetching file list…");

        Task<List<FileEntry>> task = new Task<>() {
            @Override protected List<FileEntry> call() throws Exception {
                return service.listFiles();
            }
        };

        task.setOnSucceeded(e -> {
            files.setAll(task.getValue());
            log("Listed " + task.getValue().size() + " file(s).");
            setBusy(false, "");
        });

        task.setOnFailed(e -> {
            log("ERROR listing: " + task.getException().getMessage());
            setBusy(false, "");
        });

        executor.execute(task);
    }

    // send file up
    @FXML
    private void handleUpload() {
        if (!checkConnected()) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File to Upload");
        File file = chooser.showOpenDialog(fileTable.getScene().getWindow());
        if (file == null) return;

        // Ask for a server-side name (default = local name)
        TextInputDialog nameDialog = new TextInputDialog(file.getName());
        nameDialog.setTitle("Upload File");
        nameDialog.setHeaderText("Name to store on the server:");
        nameDialog.setContentText("File name:");
        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().isBlank()) return;

        String serverName = nameResult.get().trim();

        log("Uploading: " + file.getName() + "  (" + fmtSize(file.length()) + ")");
        setBusy(true, "Uploading…");
        progressBar.setProgress(0);

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return service.uploadFile(serverName, file, pct -> Platform.runLater(() -> {
                    progressBar.setProgress(pct);
                    progressLabel.setText(String.format("%.0f%%", pct * 100));
                }));
            }
        };

        task.setOnSucceeded(e -> {
            log("[Server] " + task.getValue());
            setBusy(false, "Upload complete");
            progressBar.setProgress(1.0);
            handleList();
        });

        task.setOnFailed(e -> {
            log("ERROR uploading: " + task.getException().getMessage());
            setBusy(false, "Upload failed");
        });

        executor.execute(task);
    }

    // get file down
    @FXML
    private void handleDownload() {
        if (!checkConnected()) return;

        // Use selected row, or ask
        FileEntry selected = fileTable.getSelectionModel().getSelectedItem();
        String serverName;

        if (selected != null) {
            serverName = selected.getName();
        } else {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Download File");
            d.setHeaderText("Enter the file name on the server:");
            Optional<String> r = d.showAndWait();
            if (r.isEmpty() || r.get().isBlank()) return;
            serverName = r.get().trim();
        }

        FileChooser saveDlg = new FileChooser();
        saveDlg.setTitle("Save Downloaded File As");
        saveDlg.setInitialFileName(serverName);
        File dest = saveDlg.showSaveDialog(fileTable.getScene().getWindow());
        if (dest == null) return;

        log("Downloading: " + serverName);
        setBusy(true, "Downloading…");
        progressBar.setProgress(0);

        final String finalName = serverName;
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return service.downloadFile(finalName, dest, pct -> Platform.runLater(() -> {
                    progressBar.setProgress(pct);
                    progressLabel.setText(String.format("%.0f%%", pct * 100));
                }));
            }
        };

        task.setOnSucceeded(e -> {
            log("[Server] " + task.getValue());
            log("Saved → " + dest.getAbsolutePath());
            setBusy(false, "Download complete");
            progressBar.setProgress(1.0);
        });

        task.setOnFailed(e -> {
            log("ERROR downloading: " + task.getException().getMessage());
            setBusy(false, "Download failed");
        });

        executor.execute(task);
    }

    // kill file
    @FXML
    private void handleDelete() {
        if (!checkConnected()) return;

        FileEntry selected = fileTable.getSelectionModel().getSelectedItem();
        String serverName;

        if (selected != null) {
            serverName = selected.getName();
        } else {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Delete File");
            d.setHeaderText("Enter the file name to delete:");
            Optional<String> r = d.showAndWait();
            if (r.isEmpty() || r.get().isBlank()) return;
            serverName = r.get().trim();
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete \"" + serverName + "\" from the server?");
        confirm.setContentText("This cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        final String finalName = serverName;
        setBusy(true, "Deleting…");

        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                return service.deleteFile(finalName);
            }
        };

        task.setOnSucceeded(e -> {
            log("[Server] " + task.getValue());
            setBusy(false, "");
            handleList();
        });

        task.setOnFailed(e -> {
            log("ERROR deleting: " + task.getException().getMessage());
            setBusy(false, "");
        });

        executor.execute(task);
    }

    // help me
    private boolean checkConnected() {
        if (!service.isConnected()) {
            log("Not connected — click Connect first.");
            return false;
        }
        return true;
    }

    private void setConnected(boolean on) {
        if (on) {
            statusDot.setText("●");
            statusDot.setStyle("-fx-text-fill: #4ade80;");
            statusLabel.setText("Connected");
            connectBtn.setText("Disconnect");
            connectBtn.getStyleClass().removeAll("btn-connect");
            connectBtn.getStyleClass().add("btn-disconnect");
        } else {
            statusDot.setText("●");
            statusDot.setStyle("-fx-text-fill: #f87171;");
            statusLabel.setText("Disconnected");
            connectBtn.setText("Connect");
            connectBtn.getStyleClass().removeAll("btn-disconnect");
            connectBtn.getStyleClass().add("btn-connect");
            files.clear();
        }
        setActionsEnabled(on);
    }

    private void setActionsEnabled(boolean enabled) {
        listBtn.setDisable(!enabled);
        uploadBtn.setDisable(!enabled);
        downloadBtn.setDisable(!enabled);
        deleteBtn.setDisable(!enabled);
    }

    private void setBusy(boolean busy, String statusText) {
        progressLabel.setText(statusText);
        if (!busy) progressBar.setProgress(0);
        listBtn.setDisable(busy || !service.isConnected());
        uploadBtn.setDisable(busy || !service.isConnected());
        downloadBtn.setDisable(busy || !service.isConnected());
        deleteBtn.setDisable(busy || !service.isConnected());
    }

    private void log(String message) {
        String line = "[" + LocalTime.now().format(TIME_FMT) + "] " + message + "\n";
        Platform.runLater(() -> {
            logArea.appendText(line);
        });
    }

    @FXML
    private void handleClearLog() {
        logArea.clear();
    }

    private String fmtSize(long s) {
        if (s < 1_024)          return s + " B";
        if (s < 1_048_576)      return String.format("%.1f KB", s / 1_024.0);
        if (s < 1_073_741_824L) return String.format("%.1f MB", s / 1_048_576.0);
        return                         String.format("%.2f GB", s / 1_073_741_824.0);
    }
}
